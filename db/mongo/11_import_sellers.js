// ============================================================================
// 11_import_sellers.js - Tao 100 NHA BAN THAT (user + shop) trong MongoDB
//
//   Du lieu (email/slug/mat khau/hash bcrypt that) duoc nap tu file sinh tu dong
//   db/mongo/11_sellers_data.js  <--  db/seed/sellers_real.txt
//
// Chay: tools\mongo-import-sellers.bat
// Idempotent: chay lai -> cap nhat, khong tao trung (tim theo email).
// ============================================================================

const DATA_FILE = process.env.SELLERS_DATA || "db/mongo/11_sellers_data.js";
load(DATA_FILE);
if (typeof SELLERS === "undefined") {
  throw new Error("Khong nap duoc du lieu nha ban tu " + DATA_FILE);
}

const NOW = new Date();
const CREATE_ATTEMPTS = 200; // tranh trung _id khi counters lech

// 8 thanh pho that + toa do that (de index 2dsphere hop le)
const CITIES = [
  { name: "Ha Noi", province: "Ha Noi", coords: [105.8342, 21.0278], street: "Duong Sach" },
  { name: "Thanh Pho Ho Chi Minh", province: "Thanh Pho Ho Chi Minh", coords: [106.6297, 10.8231], street: "Duong Nguyen Hue" },
  { name: "Da Nang", province: "Da Nang", coords: [108.2022, 16.0544], street: "Duong Bach Dang" },
  { name: "Hai Phong", province: "Hai Phong", coords: [106.6881, 20.8449], street: "Duong Dien Bien Phu" },
  { name: "Can Tho", province: "Can Tho", coords: [105.7469, 10.0452], street: "Duong Hoa Binh" },
  { name: "Hue", province: "Thua Thien Hue", coords: [107.5843, 16.4637], street: "Duong Hung Vuong" },
  { name: "Nha Trang", province: "Khanh Hoa", coords: [109.1967, 12.2388], street: "Duong Tran Phu" },
  { name: "Vinh", province: "Nghe An", coords: [105.6882, 18.6796], street: "Duong Le Loi" }
];

// Tai khoan cu (neu con) duoc doi ten sang quy uoc moi thay vi tao trung
const LEGACY_ALIASES = { "Nhã Nam": "shop_nha_nam@gmail.com" };

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

function nextId(name, collection) {
  const counter = db.counters.findOne({ _id: name });
  let seq = Math.max(asNumber(counter && counter.seq), maxIdOf(collection));
  seq = seq + 1;
  db.counters.updateOne(
    { _id: name },
    { $set: { seq: NumberLong(String(seq)), updatedAt: NOW }, $setOnInsert: { createdAt: NOW } },
    { upsert: true }
  );
  return seq;
}

// ---------------------------------------------------------------------------
// 0) Tai khoan ADMIN that (dang nhap duoc) - idempotent
const ADMIN_EMAIL = "admin@gmail.com";
const ADMIN_HASH = "$2a$12$hjHxlMuay0sJQWnRBNQdv.8FuZsZ78IhmFxFuQC6ImUbhC0lFb27y"; // Admin123@
let adminUser = db.users.findOne({ username: ADMIN_EMAIL });
if (!adminUser) {
  const adminId = NumberLong(String(nextId("users", "users")));
  db.users.updateOne({ _id: adminId }, {
    $set: {
      username: ADMIN_EMAIL,
      passwordHash: ADMIN_HASH,
      role: "ADMIN",
      isActive: true,
      email: ADMIN_EMAIL,
      avatarUrl: "/images/avatars/admin.png",
      addresses: [],
      favoriteCategoryIds: [],
      wishlistBookIds: [],
      schemaVersion: NumberInt(1),
      createdAt: NOW,
      updatedAt: NOW
    }
  }, { upsert: true });
  print("Da tao tai khoan ADMIN: " + ADMIN_EMAIL + " / Admin123@  (_id=" + adminId + ")");
} else {
  db.users.updateOne({ _id: adminUser._id }, {
    $set: { passwordHash: ADMIN_HASH, role: "ADMIN", isActive: true, email: ADMIN_EMAIL, updatedAt: NOW }
  });
  print("Da cap nhat tai khoan ADMIN: " + ADMIN_EMAIL + " / Admin123@  (_id=" + adminUser._id + ")");
}

// ---------------------------------------------------------------------------
print("==============================================================");
print(" 11_import_sellers.js | " + SELLERS.length + " nha ban that");
print("==============================================================");

let createdUsers = 0, updatedUsers = 0, createdShops = 0;
const report = [];

SELLERS.forEach(function (s, index) {
  const city = CITIES[index % CITIES.length];

  // 1) Tim user: theo email moi -> theo alias cu (vd shop_nha_nam@gmail.com)
  let user = db.users.findOne({ username: s.email });
  const alias = LEGACY_ALIASES[s.name];
  if (!user && alias) {
    user = db.users.findOne({ username: alias });
  }

  let userId;
  if (user) {
    userId = user._id;
    updatedUsers++;
  } else {
    userId = NumberLong(String(nextId("users", "users")));
    createdUsers++;
  }

  const address = (100 + (index % 800)) + " " + city.street + ", " + city.name;
  const avatar = "/images/avatars/u" + asNumber(userId) + ".png";

  const userDoc = {
    username: s.email,
    passwordHash: s.hash,
    role: "SELLER",
    isActive: true,
    email: s.email,
    avatarUrl: avatar,
    shopName: s.name,
    shopAddress: address,
    addresses: [],
    favoriteCategoryIds: [],
    wishlistBookIds: [],
    schemaVersion: NumberInt(1),
    createdAt: user && user.createdAt ? user.createdAt : NOW,
    updatedAt: NOW
  };
  db.users.updateOne({ _id: userId }, { $set: userDoc }, { upsert: true });

  // 2) Shop (APPROVED de panel nghiep vu chay duoc)
  let shop = db.seller_shops.findOne({ sellerId: userId });
  if (!shop) {
    shop = db.seller_shops.findOne({ slug: s.slug });
  }
  let shopId;
  if (shop) {
    shopId = shop._id;
  } else {
    shopId = NumberLong(String(nextId("seller_shops", "seller_shops")));
    createdShops++;
  }

  const shopDoc = {
    sellerId: NumberLong(String(userId)),
    slug: s.slug,
    shopName: s.name,
    description: s.name + " - nha ban sach chinh hang",
    seller: {
      id: NumberLong(String(userId)),
      username: s.email,
      firstName: "",
      lastName: "",
      fullName: s.name,
      shopName: s.name,
      email: s.email,
      phone: null,
      avatarUrl: avatar
    },
    logoUrl: "/uploads/shops/logo_" + asNumber(userId) + ".jpg",
    bannerUrl: "/uploads/shops/banner_" + asNumber(userId) + ".jpg",
    contactEmail: s.email,
    contactPhone: "09" + String(10000000 + index * 137).substring(0, 8),
    address: address,
    city: city.name,
    province: city.province,
    location: { type: "Point", coordinates: city.coords },
    rejectionReason: null,
    approvalStatus: "APPROVED",
    followerCount: NumberLong("0"),
    rating: 0,
    ratingCount: NumberLong("0"),
    stats: {
      productCount: NumberInt(0),
      orderCount30d: NumberLong("0"),
      revenue30d: 0,
      responseRate: 0
    },
    createdAt: shop && shop.createdAt ? shop.createdAt : NOW,
    updatedAt: NOW
  };
  db.seller_shops.updateOne({ _id: shopId }, { $set: shopDoc }, { upsert: true });

  report.push({ name: s.name, email: s.email, password: s.password, userId: asNumber(userId), shopId: asNumber(shopId) });
});


// ---------------------------------------------------------------------------
// 3) Dong bo counters (khong de trung _id khi app tao ban ghi moi)
const maxUser = db.users.find({}).sort({ _id: -1 }).limit(1).toArray()[0];
const maxShop = db.seller_shops.find({}).sort({ _id: -1 }).limit(1).toArray()[0];
db.counters.updateOne({ _id: "users" },
  { $set: { seq: NumberLong(String(maxUser ? asNumber(maxUser._id) : 0)), updatedAt: NOW } });
db.counters.updateOne({ _id: "seller_shops" },
  { $set: { seq: NumberLong(String(maxShop ? asNumber(maxShop._id) : 0)), updatedAt: NOW } });

// 4) Ket qua
print("Tao moi user: " + createdUsers + " | cap nhat user: " + updatedUsers + " | tao moi shop: " + createdShops);
print("Tong users=" + db.users.countDocuments() + " | seller=" + db.users.countDocuments({ role: "SELLER" })
  + " | shop=" + db.seller_shops.countDocuments() + " | shop APPROVED="
  + db.seller_shops.countDocuments({ approvalStatus: "APPROVED" }));
print("");
print("=== 10 TAI KHOAN DAU TIEN (dang nhap bang EMAIL + MAT KHAU) ===");
report.slice(0, 10).forEach(function (r) {
  print("   " + r.email + "  /  " + r.password + "   (" + r.name + ")");
});
print("   ... (day du trong db/mongo/11_sellers_data.js)");
print("");
print("Buoc tiep theo: tools\\mongo-import-books.bat (nhap toan bo sach tu Books.csv)");
