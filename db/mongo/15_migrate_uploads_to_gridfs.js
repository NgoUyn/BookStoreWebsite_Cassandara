// ============================================================================
// 15_migrate_uploads_to_gridfs.js
//
// MUC DICH: chuyen toan bo anh dang nam tren dia (uploads/covers,
//   uploads/shops, uploads/legal-docs va URL /images/covers cu) vao GridFS
//   (fs.files + fs.chunks) cua chinh database bookom.
//   => Tu day `mongodump --gzip --archive` sao luu duoc CA DU LIEU LAN ANH
//      trong 1 file, va restore o may khac la anh hien lai ngay.
//
// DOI TUONG XU LY:
//   books.imageUrl / mediumImageUrl / largeimageUrl + books.images{...}
//        -> /api/files/{objectId}  va ghi them books.coverFileId
//   seller_shops.logoUrl / bannerUrl
//        -> /api/files/{objectId}  va ghi them logoFileId / bannerFileId
//   seller_shops.legalDocuments[].imageUrl
//        -> /api/files/{objectId}  va ghi them fileId
//
// KHONG DONG TOI: users.avatarUrl, reviews.images (URL tham chieu anh mau
//   khong ton tai trong repo) - script chi THONG KE o phan cuoi.
//
// CACH CHAY:
//   tools\migrate-uploads-to-gridfs.bat                 -> DRY RUN (chi in)
//   set APPLY=1 && tools\migrate-uploads-to-gridfs.bat  -> ghi that
//
// IDEMPOTENT: URL da o dang /api/files/... se duoc bo qua (chay lai khong
//   tao them ban sao file).
// ============================================================================

const APPLY = String(process.env.APPLY || "").trim() === "1";
const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);

// Thu muc goc cua project (bat truyen vao) - dung de doc file tren dia
const ROOT = String(process.env.UPLOAD_ROOT || ".")
  .replace(/\\/g, "/")
  .replace(/\/+$/, "");

const CHUNK_SIZE = 255 * 1024;          // 255 KB - dung chuan GridFS
const API_PREFIX = "/api/files/";
const FILES = db.getCollection("fs.files");
const CHUNKS = db.getCollection("fs.chunks");

const stats = {
  docsChanged: 0,
  fieldsMigrated: 0,
  bytes: 0,
  alreadyGridFs: 0,
  missingFile: 0
};

// Cache theo duong dan dia: 1 file duoc nhieu field tham chieu -> chi luu 1 lan
const cache = {};

// ---------------------------------------------------------------------------
// Helper: URL legacy -> danh sach duong dan dia co the chua file
// ---------------------------------------------------------------------------
function candidatePaths(url) {
  if (url.indexOf("/uploads/covers/") === 0) {
    return [ROOT + url, ROOT + "/src/main/resources/static" + url];
  }
  if (url.indexOf("/images/covers/") === 0) {
    // WebConfig phuc vu /images/covers/ tu src/main/resources/static;
    // fallback uploads/covers/ (anh do BookService ban cu ghi ra)
    return [
      ROOT + "/src/main/resources/static" + url,
      ROOT + "/uploads/covers/" + url.substring("/images/covers/".length)
    ];
  }
  if (url.indexOf("/uploads/shops/") === 0 || url.indexOf("/uploads/legal-docs/") === 0) {
    return [ROOT + url];
  }
  return [];
}

function isLegacyImageUrl(url) {
  return typeof url === "string" && url.length > 1 && /^\/(uploads|images)\//.test(url);
}

function contentTypeOf(path) {
  const lower = path.toLowerCase();
  if (lower.endsWith(".png")) return "image/png";
  if (lower.endsWith(".webp")) return "image/webp";
  return "image/jpeg";
}

function basename(path) {
  const parts = path.split("/");
  return parts[parts.length - 1];
}


// ---------------------------------------------------------------------------
// Helper: ghi 1 file vao GridFS theo dung spec (fs.files + fs.chunks)
// ---------------------------------------------------------------------------
function extensionOf(path) {
  const lower = basename(path).toLowerCase();
  if (lower.endsWith(".png")) return "png";
  if (lower.endsWith(".webp")) return "webp";
  return "jpg";
}

function storeToGridFs(buffer, absPath, scope) {
  const id = new ObjectId();
  const filename = scope + "_" + id.toHexString() + "." + extensionOf(absPath);

  FILES.insertOne({
    _id: id,
    length: NumberLong(buffer.length),
    chunkSize: NumberInt(CHUNK_SIZE),
    uploadDate: new Date(),
    filename: filename,
    metadata: {
      scope: scope,
      contentType: contentTypeOf(absPath),
      originalName: basename(absPath),
      migratedFrom: "uploads/",
      migratedAt: new Date()
    }
  });

  let n = 0;
  for (let offset = 0; offset < buffer.length; offset += CHUNK_SIZE) {
    const slice = buffer.slice(offset, Math.min(offset + CHUNK_SIZE, buffer.length));
    CHUNKS.insertOne({
      _id: new ObjectId(),
      files_id: id,
      n: NumberInt(n),
      data: BinData(0, slice.toString("base64"))
    });
    n++;
  }
  return id;
}

// ---------------------------------------------------------------------------
// Helper: migrate 1 URL. Tra ve { changed, url, fileId }
// ---------------------------------------------------------------------------
function migrateUrl(url, scope) {
  if (!url || typeof url !== "string") return { changed: false };
  if (url.indexOf(API_PREFIX) === 0) {
    stats.alreadyGridFs++;
    return { changed: false };
  }
  if (!isLegacyImageUrl(url)) return { changed: false };

  const absPath = candidatePaths(url).filter(function (p) { return fs.existsSync(p); })[0];
  if (!absPath) {
    stats.missingFile++;
    print("   [thieu file tren dia] " + scope + " : " + url);
    return { changed: false };
  }

  let entry = cache[absPath];
  if (!entry) {
    const buffer = fs.readFileSync(absPath);
    if (buffer.length === 0) {
      stats.missingFile++;
      print("   [file rong - bo qua] " + absPath);
      return { changed: false };
    }
    entry = {
      fileId: APPLY ? storeToGridFs(buffer, absPath, scope) : null,
      size: buffer.length
    };
    entry.url = API_PREFIX + (entry.fileId ? entry.fileId.toHexString() : "<objectId-moi>");
    cache[absPath] = entry;
    stats.bytes += buffer.length;
  }

  stats.fieldsMigrated++;
  return { changed: true, url: entry.url, fileId: entry.fileId };
}

// ===========================================================================
// BAT DAU MIGRATE
// ===========================================================================
print("==============================================================");
print(" 15_migrate_uploads_to_gridfs.js | che do = " + (APPLY ? "APPLY (GHI THAT)" : "DRY RUN (khong ghi)"));
print("==============================================================");
print("DB          : " + DB_NAME);
print("Upload root : " + ROOT);
print("");

// Index chuan cua GridFS (idempotent) - giup doc chunk nhanh va khong trung
if (APPLY) {
  CHUNKS.createIndex({ files_id: 1, n: 1 }, { unique: true });
}

// ---------------------------------------------------------------------------
// 0. Kho anh dang nam tren dia (doi chieu)
//    File KHONG duoc document nao tham chieu thi khong the migrate (khong con
//    URL tro toi) - chi bao so luong de biet ma don dep.
// ---------------------------------------------------------------------------
print("[0] Anh dang nam tren dia");
[
  ["uploads/covers", "uploads/covers"],
  ["uploads/shops", "uploads/shops"],
  ["uploads/legal-docs", "uploads/legal-docs"],
  ["covers cu (static)", "src/main/resources/static/images/covers"]
].forEach(function (pair) {
  let count = -1;
  try {
    count = fs.readdirSync(ROOT + "/" + pair[1])
      .filter(function (n) { return /\.(jpg|jpeg|png|webp)$/i.test(n); }).length;
  } catch (e) {
    count = -1;
  }
  print("   " + pair[0] + " : " + (count < 0 ? "(khong ton tai)" : count + " file"));
});
print("");

// ---------------------------------------------------------------------------
// 1. books - anh bia sach
// ---------------------------------------------------------------------------
print("[1] books - anh bia");
const COVER_FIELDS = ["imageUrl", "mediumImageUrl", "largeImageUrl", "largeimageUrl"];
const LEGACY_URL_MATCH = { $regex: "^/(uploads|images)/" };

// Chi lay document THUC SU con URL anh cuc bo (496k sach cua dataset dung URL
// http ngoai -> loc ngay tren server, khong keo het ve mongosh).
const BOOK_COVER_FILTER = {
  $or: [
    { imageUrl: LEGACY_URL_MATCH },
    { mediumImageUrl: LEGACY_URL_MATCH },
    { largeImageUrl: LEGACY_URL_MATCH },
    { largeimageUrl: LEGACY_URL_MATCH },
    { "images.thumbnail": LEGACY_URL_MATCH },
    { "images.medium": LEGACY_URL_MATCH },
    { "images.large": LEGACY_URL_MATCH }
  ]
};

db.books.find(BOOK_COVER_FILTER).forEach(function (book) {
  const set = {};

  COVER_FIELDS.forEach(function (field) {
    const r = migrateUrl(book[field], "covers");
    if (r.changed) {
      set[field] = r.url;
      if (field === "imageUrl" && r.fileId) set.coverFileId = r.fileId;
    }
  });

  // books.images{thumbnail, medium, large}
  if (book.images && typeof book.images === "object") {
    const images = Object.assign({}, book.images);
    let imagesChanged = false;
    ["thumbnail", "medium", "large"].forEach(function (key) {
      const r = migrateUrl(images[key], "covers");
      if (r.changed) {
        images[key] = r.url;
        imagesChanged = true;
      }
    });
    if (imagesChanged) set.images = images;
  }

  if (Object.keys(set).length) {
    stats.docsChanged++;
    if (APPLY) db.books.updateOne({ _id: book._id }, { $set: set });
    print("   book _id=" + book._id + " -> " + (set.imageUrl || "(khong doi imageUrl)"));
  }
});

// ---------------------------------------------------------------------------
// 2. seller_shops - logo/banner + anh tai lieu phap ly
// ---------------------------------------------------------------------------
print("");
print("[2] seller_shops - logo/banner + tai lieu phap ly");
const SHOP_IMAGE_FILTER = {
  $or: [
    { logoUrl: LEGACY_URL_MATCH },
    { bannerUrl: LEGACY_URL_MATCH },
    { "legalDocuments.imageUrl": LEGACY_URL_MATCH }
  ]
};

db.seller_shops.find(SHOP_IMAGE_FILTER).forEach(function (shop) {
  const set = {};

  const logo = migrateUrl(shop.logoUrl, "shops");
  if (logo.changed) {
    set.logoUrl = logo.url;
    if (logo.fileId) set.logoFileId = logo.fileId.toHexString();
  }

  const banner = migrateUrl(shop.bannerUrl, "shops");
  if (banner.changed) {
    set.bannerUrl = banner.url;
    if (banner.fileId) set.bannerFileId = banner.fileId.toHexString();
  }

  if (Array.isArray(shop.legalDocuments) && shop.legalDocuments.length) {
    let docsChanged = false;
    const docs = shop.legalDocuments.map(function (doc) {
      if (!doc) return doc;
      const r = migrateUrl(doc.imageUrl, "legal-docs");
      if (!r.changed) return doc;
      docsChanged = true;
      const copy = Object.assign({}, doc);
      copy.imageUrl = r.url;
      if (r.fileId) copy.fileId = r.fileId.toHexString();
      return copy;
    });
    if (docsChanged) set.legalDocuments = docs;
  }

  if (Object.keys(set).length) {
    stats.docsChanged++;
    set.updatedAt = new Date();
    if (APPLY) db.seller_shops.updateOne({ _id: shop._id }, { $set: set });
    print("   shop _id=" + shop._id + " | " + shop.shopName + " -> " + Object.keys(set).join(", "));
  }
});

// ---------------------------------------------------------------------------
// 3. Bao cao
// ---------------------------------------------------------------------------
print("");
print("--- URL LEGACY CON LAI (script nay KHONG migrate) ---");
print("   users.avatarUrl : " + db.users.countDocuments({ avatarUrl: { $regex: "^/(uploads|images)/" } })
  + "  (anh mau khong ton tai trong repo)");
print("   reviews.images  : " + db.reviews.countDocuments({ images: { $regex: "^/(uploads|images)/" } })
  + "  (anh mau khong ton tai trong repo)");

const booksLegacy = db.books.countDocuments({
  $or: [
    { imageUrl: { $regex: "^/(uploads|images)/" } },
    { mediumImageUrl: { $regex: "^/(uploads|images)/" } },
    { largeImageUrl: { $regex: "^/(uploads|images)/" } },
    { largeimageUrl: { $regex: "^/(uploads|images)/" } }
  ]
});
const shopsLegacy = db.seller_shops.countDocuments({
  $or: [
    { logoUrl: { $regex: "^/(uploads|images)/" } },
    { bannerUrl: { $regex: "^/(uploads|images)/" } }
  ]
});

print("");
print("==============================================================");
print(" KET QUA " + (APPLY ? "(DA GHI)" : "(DRY RUN - chua ghi gi)"));
print("==============================================================");
print(" document duoc cap nhat        : " + stats.docsChanged);
print(" field anh da chuyen           : " + stats.fieldsMigrated);
print(" dung luong anh                : " + (stats.bytes / 1024).toFixed(1) + " KB / "
  + Object.keys(cache).length + " file");
print(" URL da la /api/files/ (bo qua): " + stats.alreadyGridFs);
print(" file thieu tren dia / rong    : " + stats.missingFile);
print("");
print(" fs.files  : " + FILES.countDocuments({}));
print(" fs.chunks : " + CHUNKS.countDocuments({}));
print(" books con URL legacy          : " + booksLegacy);
print(" seller_shops con URL legacy   : " + shopsLegacy);
print("");
if (!APPLY) {
  print("DRY RUN - chua ghi gi. Chay lai voi APPLY=1 de ghi that:");
  print("   set APPLY=1 && tools\\migrate-uploads-to-gridfs.bat");
} else if (booksLegacy === 0 && shopsLegacy === 0) {
  print("OK - anh bia sach / logo / banner / tai lieu phap ly deu da nam trong GridFS.");
  print("     Tu day: tools\\mongo-backup.bat sao luu duoc CA DU LIEU LAN ANH.");
} else {
  print("CANH BAO: van con URL legacy - xem danh sach [thieu file tren dia] o tren.");
}
