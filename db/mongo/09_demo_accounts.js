// ============================================================================
// 09_demo_accounts.js - Tao 2 TAI KHOAN THAT dang nhap duoc + shop + gan sach
//
//   SELLER : shop_nha_nam@gmail.com / Nhanam123@   (shop "Nha Nam Official")
//   ADMIN  : admin@gmail.com        / Admin123@
//
// Chay:  tools\mongo-demo-accounts.bat
//   hoac mongosh "<uri>" --file db/mongo/09_demo_accounts.js
//
// Dac diem:
//   - Idempotent: chay lai nhieu lan khong tao trung (tim theo username).
//   - passwordHash la bcrypt THAT (cost 12 = dung bang AuthService) nen
//     BCrypt.checkpw() cua ung dung khop, khac voi tai khoan seed
//     ($2a$10$seedHash<id> - hash gia, khong dang nhap duoc).
//   - _id lay tu collection `counters` va ghi lai seq => khong trung _id
//     voi du lieu app tao sau nay.
//   - BSON type dung NumberLong/NumberInt de qua $jsonSchema validator.
// ============================================================================

const SELLER_EMAIL = "shop_nha_nam@gmail.com";
const SELLER_HASH  = "$2a$12$//xtPUSFuglxaC.NcIFv6eLEfpIoS.VM7UpX/BcfHffkewvhGiYde"; // Nhanam123@
const ADMIN_EMAIL  = "admin@gmail.com";
const ADMIN_HASH   = "$2a$12$hjHxlMuay0sJQWnRBNQdv.8FuZsZ78IhmFxFuQC6ImUbhC0lFb27y"; // Admin123@
const SHOP_SLUG    = "nha-nam-official";
const SHOP_NAME    = "Nha Nam Official";
const CITY         = "Ha Noi";
const BOOKS_TO_ASSIGN = 20;
const NOW = new Date();

print("==============================================================");
print(" 09_demo_accounts.js - tao tai khoan demo (SELLER + ADMIN)");
print("==============================================================");

// --- 1. Helper: doc counter (seq co the la Long hoac Int32) -----------------
function asNumber(v) {
  if (v === null || v === undefined) return 0;
  if (typeof v === "number") return v;
  if (typeof v.toNumber === "function") return v.toNumber();
  return Number(v);
}

function maxIdOf(collection) {
  const last = db.getCollection(collection).find({}).sort({ _id: -1 }).limit(1).toArray()[0];
  return last ? asNumber(last._id) : 0;
}

/**Cap `count` id lien tiep cho collection `name` (tu counters + max _id hien co).*/
function allocateIds(name, collection, count) {
  const counter = db.counters.findOne({ _id: name });
  let seq = Math.max(asNumber(counter && counter.seq), maxIdOf(collection));
  const ids = [];
  for (let i = 0; i < count; i++) {
    seq = seq + 1;
    ids.push(seq);
  }
  db.counters.updateOne(
    { _id: name },
    { $set: { seq: NumberLong(String(seq)), updatedAt: NOW }, $setOnInsert: { createdAt: NOW } },
    { upsert: true }
  );
  print("   [counters] " + name + ": seq -> " + seq);
  return ids;
}

// --- 2. Tao/cap nhat 2 tai khoan -------------------------------------------
function upsertAccount(email, passwordHash, role, avatarUrl, extra) {
  const existing = db.users.findOne({ username: email });
  let id;
  if (existing) {
    id = existing._id;
    print("   [users] da co: " + email + " (_id=" + id + ") -> cap nhat hash + role");
  } else {
    id = NumberLong(String(allocateIds("users", "users", 1)[0]));
    print("   [users] tao moi: " + email + " (_id=" + id + ")");
  }

  const doc = {
    username: email,
    passwordHash: passwordHash,
    role: role,
    isActive: true,
    email: email,
    avatarUrl: avatarUrl,
    addresses: existing && existing.addresses ? existing.addresses : [],
    favoriteCategoryIds: existing && existing.favoriteCategoryIds ? existing.favoriteCategoryIds : [],
    wishlistBookIds: existing && existing.wishlistBookIds ? existing.wishlistBookIds : [],
    schemaVersion: NumberInt(1),
    createdAt: existing && existing.createdAt ? existing.createdAt : NOW,
    updatedAt: NOW
  };
  if (extra) {
    Object.keys(extra).forEach(function (k) { doc[k] = extra[k]; });
  }
  db.users.updateOne({ _id: id }, { $set: doc }, { upsert: true });
  return db.users.findOne({ _id: id });
}


// --- 3. Tao/cap nhat 2 tai khoan -------------------------------------------
print("");
print("[1/4] TAI KHOAN ADMIN");
const admin = upsertAccount(ADMIN_EMAIL, ADMIN_HASH, "ADMIN", "/images/avatars/admin.png", null);

print("[2/4] TAI KHOAN SELLER");
const seller = upsertAccount(SELLER_EMAIL, SELLER_HASH, "SELLER", "/images/avatars/nhanam.png", {
  shopName: SHOP_NAME,
  shopAddress: "59 Duong Sach, " + CITY
});

// --- 4. Shop cua seller (phai APPROVED thi panel/nghiep vu moi chay) -------
print("[3/4] SHOP cua seller");
const oldShop = db.seller_shops.findOne({ sellerId: seller._id });
const shopId = oldShop ? oldShop._id : NumberLong(String(allocateIds("seller_shops", "seller_shops", 1)[0]));

const shopDoc = {
  sellerId: NumberLong(String(seller._id)),
  slug: SHOP_SLUG,
  shopName: SHOP_NAME,
  description: "Nha sach Nha Nam - van hoc, ky nang song, kinh te",
  seller: {
    id: NumberLong(String(seller._id)),
    username: seller.username,
    firstName: "Nha",
    lastName: "Nam",
    fullName: "Nha Nam",
    email: SELLER_EMAIL,
    phone: "0912345678",
    avatarUrl: seller.avatarUrl
  },
  logoUrl: "/uploads/shops/logo_nhanam.jpg",
  bannerUrl: "/uploads/shops/banner_nhanam.jpg",
  contactEmail: SELLER_EMAIL,
  contactPhone: "0912345678",
  address: "59 Duong Sach",
  city: CITY,
  province: CITY,
  location: { type: "Point", coordinates: [105.8342, 21.0278] },
  rejectionReason: null,
  approvalStatus: "APPROVED",
  followerCount: NumberLong("1250"),
  rating: 4.8,
  ratingCount: NumberLong("320"),
  stats: {
    productCount: NumberInt(0),
    orderCount30d: NumberLong("0"),
    revenue30d: 0,
    responseRate: 1.0
  },
  createdAt: oldShop && oldShop.createdAt ? oldShop.createdAt : NOW,
  updatedAt: NOW
};
db.seller_shops.updateOne({ _id: shopId }, { $set: shopDoc }, { upsert: true });
print("   [seller_shops] _id=" + shopId + " slug=" + SHOP_SLUG + " status=APPROVED");

// --- 5. Gan N sach seed cho seller (de /seller/inventory co du lieu) -------
print("[4/4] GAN " + BOOKS_TO_ASSIGN + " SACH cho seller");
const bookIds = db.books
  .find({ approvalStatus: "APPROVED", isActive: true })
  .sort({ _id: -1 })
  .limit(BOOKS_TO_ASSIGN)
  .toArray()
  .map(function (b) { return b._id; });

const sellerSnapshot = {
  id: NumberLong(String(seller._id)),
  username: seller.username,
  firstName: "Nha",
  lastName: "Nam",
  fullName: "Nha Nam",
  shopName: SHOP_NAME,
  email: SELLER_EMAIL,
  phone: "0912345678",
  avatarUrl: seller.avatarUrl
};

if (bookIds.length > 0) {
  const upd = db.books.updateMany(
    { _id: { $in: bookIds } },
    { $set: { sellerId: NumberLong(String(seller._id)), seller: sellerSnapshot, updatedAt: NOW } }
  );
  db.seller_shops.updateOne(
    { _id: shopId },
    { $set: { "stats.productCount": NumberInt(bookIds.length), updatedAt: NOW } }
  );
  print("   [books] da doi chu " + upd.modifiedCount + " sach sang seller " + seller._id);
} else {
  print("   [books] KHONG tim thay sach APPROVED nao - bo qua buoc gan sach");
}

// --- 6. Mo khoa tai khoan dang ky qua UI truoc khi sua bug ------------------
print("[5/5] MO KHOA tai khoan dang ky qua UI truoc khi sua bug (isActive=false)");
const locked = db.users.find({ _id: { $gt: 1300 }, isActive: false }).toArray();
if (locked.length === 0) {
  print("   khong co tai khoan nao bi khoa");
} else {
  db.users.updateMany(
    { _id: { $gt: 1300 }, isActive: false },
    { $set: { isActive: true, updatedAt: NOW } }
  );
  locked.forEach(function (u) {
    print("   da mo khoa: " + u.username + " (_id=" + u._id + ")");
  });
}

// --- 7. Ket qua -------------------------------------------------------------
const sellerDb = db.users.findOne({ _id: seller._id });
const adminDb = db.users.findOne({ _id: admin._id });
const shopDb = db.seller_shops.findOne({ _id: shopId });

print("");
print("==================== KET QUA ====================");
print("SELLER : " + sellerDb.username + " | _id=" + sellerDb._id + " | role=" + sellerDb.role
  + " | isActive=" + sellerDb.isActive + " | hash=" + String(sellerDb.passwordHash).substring(0, 7));
print("ADMIN  : " + adminDb.username + " | _id=" + adminDb._id + " | role=" + adminDb.role
  + " | isActive=" + adminDb.isActive + " | hash=" + String(adminDb.passwordHash).substring(0, 7));
print("SHOP   : " + shopDb.shopName + " | slug=" + shopDb.slug + " | " + shopDb.approvalStatus
  + " | productCount=" + shopDb.stats.productCount);
print("BOOKS  : " + db.books.countDocuments({ sellerId: seller._id }) + " sach thuoc seller nay");
print("------------------------------------------------");
print("DANG NHAP BANG EMAIL + MAT KHAU:");
print("  SELLER : " + SELLER_EMAIL + "  /  Nhanam123@");
print("  ADMIN  : " + ADMIN_EMAIL + "  /  Admin123@");
print("=================================================");
