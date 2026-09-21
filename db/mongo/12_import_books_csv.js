// ============================================================================
// 12_import_books_csv.js - Chuyen books_raw (mongoimport tu Books.csv) thanh
//                          collection `books` dung schema cua ung dung.
//
//   Nguon: src/main/resources/Books.csv  (dataset Book-Crossing, ~270k dong)
//   Cach chay: tools\mongo-import-books.bat   (mongoimport -> script nay)
//
//   Quy uoc (giong DatabaseSeederService.readAndSaveFromCsv):
//     Image-URL-L -> images.large (va getImageUrl() tra ve thumbnail)
//     price/stockQuantity/categoryId: sinh theo hash cua ISBN (ON DINH, khong doi
//     khi chay lai) vi CSV khong co cac cot nay.
//     sellerId: phan bo DEU cho 100 nha ban that (round-robin theo hash).
//   _id = NumberLong(counters.books.seq + i) -> validator $jsonSchema hop le.
// ============================================================================

const RAW = "books_raw";
const BATCH = 2000;
const NOW = new Date();

function asNumber(v) {
  if (v === null || v === undefined) return 0;
  if (typeof v === "number") return v;
  if (typeof v.toNumber === "function") return v.toNumber();
  return Number(v);
}

/** Hash on dinh tu chuoi (dung de phan bo gia/tong kho/danh muc/nha ban). */
function hash(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h = h ^ str.charCodeAt(i);
    h = (h * 16777619) % 2147483647;
  }
  return Math.abs(h);
}

const categories = db.categories.find({}).sort({ _id: 1 }).toArray();
if (categories.length === 0) {
  throw new Error("Chua co `categories` - chay tools\\mongo-run-scripts.bat 01 02 03 truoc");
}
const sellers = db.users.find({ role: "SELLER" }).sort({ _id: 1 }).toArray();
if (sellers.length === 0) {
  throw new Error("Chua co seller that - chay tools\\mongo-import-sellers.bat truoc");
}
if (db.getCollection(RAW).countDocuments({}) === 0) {
  throw new Error("Chua co `" + RAW + "` - chay mongoimport truoc (xem tools\\mongo-import-books.bat)");
}

const counter = db.counters.findOne({ _id: "books" });
let seq = asNumber(counter && counter.seq);
db.books.find({}).sort({ _id: -1 }).limit(1).toArray().forEach(function (b) {
  seq = Math.max(seq, asNumber(b._id));
});

print("==============================================================");
print(" 12_import_books_csv.js");
print("   raw=" + db.getCollection(RAW).countDocuments({}) + " dong | seller=" + sellers.length
  + " | category=" + categories.length + " | books.seq bat dau = " + seq);
print("==============================================================");

const sellerDocs = sellers.map(function (u) {
  return {
    id: NumberLong(String(u._id)),
    username: u.username,
    firstName: "",
    lastName: "",
    fullName: u.shopName || u.username,
    shopName: u.shopName || u.username,
    email: u.email || u.username,
    phone: null,
    avatarUrl: u.avatarUrl || null
  };
});

const seen = new Set();
let inserted = 0, dup = 0, bad = 0, header = 0;
let batch = [];
const perSeller = {};

function flush() {
  if (batch.length === 0) return;
  db.books.insertMany(batch, { ordered: false });
  inserted += batch.length;
  batch = [];
}


// ---------------------------------------------------------------------------
// Vong lap chuyen doi
db.getCollection(RAW).find({}).noCursorTimeout().forEach(function (r) {
  const isbn = String(r.ISBN === undefined || r.ISBN === null ? "" : r.ISBN).trim();
  if (!isbn) { bad++; return; }
  if (isbn.toUpperCase() === "ISBN") { header++; return; }        // dong header (mongoimport --columnsHaveTypes)

  const title = String(r["Book-Title"] || "").trim();
  const author = String(r["Book-Author"] || "").trim();
  if (!title || !author) { bad++; return; }

  const key = (title + "|" + author).toLowerCase();
  if (seen.has(key)) { dup++; return; }
  seen.add(key);

  const h = hash(isbn);
  const category = categories[h % categories.length];
  const sellerIndex = h % sellers.length;
  const seller = sellers[sellerIndex];

  let year = parseInt(String(r["Year-Of-Publication"] || "").trim(), 10);
  if (isNaN(year) || year <= 0 || year > 2100) { year = null; }

  const publisher = String(r.Publisher || "").trim().replace(/&amp;/g, "&").replace(/\s+/g, " ");
  const imgS = String(r["Image-URL-S"] || "").trim();
  const imgM = String(r["Image-URL-M"] || "").trim();
  const imgL = String(r["Image-URL-L"] || "").trim();

  const price = 50000 + Math.round((h % 200000) / 1000) * 1000;     // 50.000 - 250.000
  const stock = 10 + (h % 90);                                     // 10 - 99
  seq = seq + 1;

  const doc = {
    _id: NumberLong(String(seq)),
    title: title,
    author: author,
    isbn: isbn,
    publisher: publisher || null,
    publishYear: year === null ? null : NumberInt(year),
    description: title + " - " + author + (publisher ? ", NXB " + publisher : "")
      + (year ? " (" + year + ")" : "") + ". Sach co san tai " + (seller.shopName || "nha sach") + ".",
    price: Double(price),
    discountAmount: NumberInt(0),
    finalPrice: Double(price),
    stockQuantity: NumberInt(stock),
    images: {
      thumbnail: imgS || "",
      medium: imgM || imgS || "",
      large: imgL || imgM || imgS || ""
    },
    coverFileId: null,
    categoryId: NumberLong(String(asNumber(category._id))),
    categoryName: category.name,
    categoryPath: [NumberLong(String(asNumber(category._id)))],
    sellerId: NumberLong(String(asNumber(seller._id))),
    seller: sellerDocs[sellerIndex],
    tags: [],
    rating: { avg: 0, count: 0, distribution: { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }, lastReviewAt: null },
    stats: { soldCount: 0, viewCount: 0, wishlistCount: 0, revenue: 0 },
    topReviews: [],
    boughtTogether: [],
    approvalStatus: "APPROVED",
    isActive: true,
    isPinned: false,
    schemaVersion: NumberInt(1),
    createdAt: new Date(NOW.getTime() - (h % 63072000000)),   // rai deu trong ~2 nam
    updatedAt: NOW
  };

  perSeller[String(seller._id)] = (perSeller[String(seller._id)] || 0) + 1;
  batch.push(doc);
  if (batch.length >= BATCH) { flush(); }
});

flush();

// ---------------------------------------------------------------------------
// counter + productCount cho tung shop
db.counters.updateOne({ _id: "books" },
  { $set: { seq: NumberLong(String(seq)), updatedAt: NOW }, $setOnInsert: { createdAt: NOW } },
  { upsert: true });

let shopUpdated = 0;
Object.keys(perSeller).forEach(function (sellerId) {
  const res = db.seller_shops.updateOne(
    { sellerId: NumberLong(sellerId) },
    { $set: { "stats.productCount": NumberInt(perSeller[sellerId]), updatedAt: NOW } }
  );
  shopUpdated += res.modifiedCount;
});

db.getCollection(RAW).drop();

print("Da nhap : " + inserted + " sach vao `books`");
print("Bo qua  : trung (title,author)=" + dup + " | thieu du lieu=" + bad + " | dong header=" + header);
print("books   : tong=" + db.books.countDocuments() + " | APPROVED="
  + db.books.countDocuments({ approvalStatus: "APPROVED" })
  + " | counter books.seq=" + seq);
print("shop    : da cap nhat productCount cho " + shopUpdated + "/" + Object.keys(perSeller).length + " nha ban");
print("raw     : da xoa collection " + RAW);
print("");
print("Buoc tiep theo: tools\\run-app.bat  ->  mo http://localhost:8080");
