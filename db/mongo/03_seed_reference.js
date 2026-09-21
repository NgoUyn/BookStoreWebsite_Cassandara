// ============================================================================
// 03_seed_reference.js
// Sinh du lieu mau DAY DU cho BOOKOM (du de chay truy van co ban + nang cao).
//
// CACH CHAY: tools\mongo-run-scripts.bat
// IDEMPOTENT: xoa sach cac collection roi sinh lai. DETERMINISTIC: cung seed
//             cho ra cung du lieu => ket qua truy van kiem chung lai duoc.
//
// QUY MO: 30 danh muc (cay cha/con) | 1 admin + 40 seller + 200 buyer
//         40 shop (co toa do) | 400 sach | 40 voucher | 150 gio hang
//         600 don hang (trai 12 thang) | 1200 review | 300 thong bao
//         200 giao dich | 60 tra hang | 40 ticket | 5000 activity log
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 03_seed_reference.js | DB = " + db.getName() + " ===");

const COLLECTIONS = [
  "users", "books", "carts", "orders", "seller_shops", "reviews", "categories",
  "coupons", "notifications", "notification_deliveries", "payment_transactions",
  "order_returns", "support_tickets", "association_rules", "refresh_tokens",
  "otp_codes", "distributed_locks", "daily_stats", "activity_log"
];

// --- Random co seed -> tai lap duoc -----------------------------------------
let _seed = 20260921;
function rnd() { _seed = (_seed * 1103515245 + 12345) % 2147483648; return _seed / 2147483648; }
function rint(min, max) { return Math.floor(rnd() * (max - min + 1)) + min; }
function pick(arr) { return arr[rint(0, arr.length - 1)]; }
function daysAgo(n) { return new Date(Date.now() - n * 86400000); }
function slugify(s) { return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, ""); }

print("--- [0/9] Xoa du lieu cu ---");
COLLECTIONS.forEach(function (c) {
  const r = db.getCollection(c).deleteMany({});
  print("    cleared " + c + ": " + r.deletedCount);
});

// ===========================================================================
// [1/9] CATEGORIES (cay cha/con -> demo $graphLookup)
// ===========================================================================
print("--- [1/9] categories ---");
const ROOTS = ["Van hoc", "Kinh te", "Ky nang song", "Thieu nhi", "Cong nghe",
               "Ngoai ngu", "Lich su", "Tam ly", "Giao khoa", "Nghe thuat"];
const SUBS = ["Co dien", "Hien dai", "Trinh tham", "Khoa hoc", "Tai chinh",
              "Khoi nghiep", "Marketing", "Lap trinh", "AI & Data", "Ngoai van"];

let catId = 1;
const categories = [];
ROOTS.forEach(function (name) {
  categories.push({
    _id: catId, name: name, slug: slugify(name), description: "Danh muc " + name,
    parentId: null, path: [catId], bookCount: 0, sortOrder: catId, isActive: true,
    createdAt: daysAgo(400), updatedAt: daysAgo(10)
  });
  catId++;
});
SUBS.forEach(function (sub) {
  const parent = categories[rint(0, ROOTS.length - 1)];
  categories.push({
    _id: catId, name: sub, slug: slugify(sub), description: "Danh muc con " + sub,
    parentId: parent._id, path: parent.path.concat([catId]), bookCount: 0,
    sortOrder: catId, isActive: true, createdAt: daysAgo(380), updatedAt: daysAgo(9)
  });
  catId++;
});
db.categories.insertMany(categories);
print("    inserted categories: " + categories.length);

// ===========================================================================
// [2/9] USERS (aggregate: profile + addresses[] + wishlist + ml + seller + stats)
// ===========================================================================
print("--- [2/9] users ---");
const FIRST = ["An", "Binh", "Chi", "Dung", "Giang", "Hai", "Hung", "Khanh", "Lan",
               "Linh", "Minh", "Nam", "Nga", "Nhung", "Phuc", "Quan", "Son", "Tam",
               "Thao", "Trang", "Tuan", "Vy", "Yen", "Bao", "Cuong"];
const LAST = ["Nguyen", "Tran", "Le", "Pham", "Hoang", "Huynh", "Phan", "Vu", "Dang", "Bui"];
const CITIES = [
  { city: "Ha Noi",      lng: 105.8342, lat: 21.0278 },
  { city: "Hai Phong",   lng: 106.6881, lat: 20.8449 },
  { city: "Da Nang",     lng: 108.2022, lat: 16.0544 },
  { city: "Ho Chi Minh", lng: 106.6297, lat: 10.8231 },
  { city: "Can Tho",     lng: 105.7469, lat: 10.0452 },
  { city: "Hue",         lng: 107.5909, lat: 16.4637 },
  { city: "Nha Trang",   lng: 109.1967, lat: 12.2388 },
  { city: "Da Lat",      lng: 108.4583, lat: 12.0239 }
];
const GENDERS = ["MALE", "FEMALE", "OTHER"];

function makeUser(id, role, isActive) {
  const first = pick(FIRST);
  const last = pick(LAST);
  const uname = role.toLowerCase() + id;
  const cityInfo = pick(CITIES);
  const createdDays = rint(60, 900);
  const u = {
    _id: id,
    username: uname,
    passwordHash: "$2a$10$seedHash" + id,
    role: role,
    email: uname + "@bookom.vn",
    phone: "09" + String(10000000 + id),
    profile: {
      firstName: first, lastName: last,
      dateOfBirth: daysAgo(rint(7000, 20000)),
      gender: pick(GENDERS),
      bio: role === "SELLER" ? "Nha ban sach uy tin" : "Doc gia yeu sach",
      avatarUrl: "/images/avatars/u" + id + ".png"
    },
    addresses: [{
      addressId: id * 10 + 1, addressType: "HOME",
      recipientName: last + " " + first, recipientPhone: "09" + String(10000000 + id),
      addressLine: rint(1, 200) + " Duong so " + rint(1, 30),
      ward: "Phuong " + rint(1, 20), district: "Quan " + rint(1, 12),
      province: cityInfo.city, postalCode: String(700000 + id),
      isDefault: true, createdAt: daysAgo(createdDays), updatedAt: daysAgo(10)
    }],
    favoriteCategoryIds: [pick(ROOTS.length > 0 ? [1, 2, 3, 4, 5] : [1]), pick([6, 7, 8, 9, 10])],
    isActive: isActive,
    createdAt: daysAgo(createdDays),
    updatedAt: daysAgo(rint(1, 30)),
    schemaVersion: 1
  };
  if (role === "SELLER") {
    u.seller = {
      shopName: "Nha sach " + last + " " + id, approved: true,
      approvedAt: daysAgo(Math.max(1, createdDays - 5))
    };
  }
  if (role === "BUYER") {
    const abandoned = rnd() < 0.25;
    u.ml = {
      accountAgeMonths: Math.round(createdDays / 30),
      avgOrderValue: rint(150, 1200) * 1000,
      totalOrders: rint(1, 30),
      customerSupportTickets: rint(0, 5),
      loyaltyMember: rnd() < 0.4 ? 1.0 : 0.0,
      browsingFrequencyPerWeek: Math.round(rnd() * 70) / 10,
      cartAbandonmentRate: abandoned ? Math.round(rnd() * 60) / 100 : Math.round(rnd() * 20) / 100,
      productReviewScoreAvg: Math.round((3 + rnd() * 2) * 10) / 10,
      satisfactionScore: Math.round((3 + rnd() * 2) * 10) / 10,
      priceSensitivityIndex: Math.round(rnd() * 100) / 100,
      discountUsageRate: Math.round(rnd() * 100) / 100,
      returnRate: Math.round(rnd() * 20) / 100,
      predictedLabel: rnd() < 0.3 ? 1 : 0,
      churnProbability: Math.round(rnd() * 100) / 100,
      riskLevel: rnd() < 0.3 ? "HIGH" : "LOW",
      lastAnalyzedAt: daysAgo(rint(1, 60))
    };
    u.stats = { orderCount: 0, totalSpent: 0, avgOrderValue: 0, reviewCount: 0, lastOrderAt: null };
    u.wishlist = { bookIds: [], updatedAt: daysAgo(rint(1, 30)) };
  }
  return u;
}

const ADMIN_ID = 1;
const users = [{
  _id: ADMIN_ID, username: "admin", passwordHash: "$2a$10$seedHashAdmin", role: "ADMIN",
  email: "admin@bookom.vn", phone: "0900000001",
  profile: { firstName: "Quan", lastName: "Tri", gender: "OTHER", avatarUrl: "/images/avatars/admin.png" },
  addresses: [], favoriteCategoryIds: [], isActive: true,
  createdAt: daysAgo(900), updatedAt: daysAgo(1), schemaVersion: 1
}];
const sellerIds = [];
const buyerIds = [];
for (let i = 2; i <= 41; i++) { users.push(makeUser(i, "SELLER", rnd() > 0.08)); sellerIds.push(i); }
for (let i = 101; i <= 300; i++) { users.push(makeUser(i, "BUYER", rnd() > 0.12)); buyerIds.push(i); }
db.users.insertMany(users);
print("    inserted users: " + users.length +
  " (admin=1, seller=" + sellerIds.length + ", buyer=" + buyerIds.length + ")");

// ===========================================================================
// [3/9] SELLER_SHOPS (aggregate: seller snapshot + geo + stats)
// ===========================================================================
print("--- [3/9] seller_shops ---");
const shops = sellerIds.map(function (sid, idx) {
  const owner = users.find(u => u._id === sid);
  const loc = CITIES[idx % CITIES.length];
  return {
    _id: 1000 + sid,
    sellerId: sid,
    slug: "shop-" + sid,
    shopName: owner.seller.shopName,
    description: "Shop sach cua " + owner.profile.lastName + " " + owner.profile.firstName,
    seller: {
      id: sid, username: owner.username,
      fullName: owner.profile.lastName + " " + owner.profile.firstName,
      email: owner.email, avatarUrl: owner.profile.avatarUrl
    },
    logoUrl: "/uploads/shops/logo_" + sid + ".jpg",
    bannerUrl: "/uploads/shops/banner_" + sid + ".jpg",
    contactEmail: owner.email,
    contactPhone: owner.phone,
    address: rint(1, 200) + " Duong Sach",
    city: loc.city,
    province: loc.city,
    location: {
      type: "Point",
      // jitter nho de moi shop co toa do khac nhau (demo $geoNear co y nghia)
      coordinates: [
        Math.round((loc.lng + (rnd() - 0.5) * 0.6) * 10000) / 10000,
        Math.round((loc.lat + (rnd() - 0.5) * 0.6) * 10000) / 10000
      ]
    },
    rejectionReason: null,
    approvalStatus: rnd() < 0.85 ? "APPROVED" : "PENDING",
    followerCount: rint(0, 5000),
    rating: Math.round((3 + rnd() * 2) * 10) / 10,
    ratingCount: rint(0, 800),
    stats: {
      productCount: 0, orderCount30d: rint(0, 300),
      revenue30d: rint(0, 90000) * 1000, responseRate: Math.round(rnd() * 100) / 100
    },
    createdAt: daysAgo(rint(60, 900)),
    updatedAt: daysAgo(rint(1, 20))
  };
});
db.seller_shops.insertMany(shops);
print("    inserted seller_shops: " + shops.length);

// ===========================================================================
// [4/9] BOOKS (aggregate: images + rating summary + stats + tags + seller snapshot)
// ===========================================================================
print("--- [4/9] books ---");
const T1 = ["Nhung nguoi", "Cau chuyen", "Hanh trinh", "Bi mat", "Nghe thuat",
            "Tu duy", "Suc manh", "Kham pha", "Dinh cao", "Bai hoc"];
const T2 = ["khoi nghiep", "tai chinh ca nhan", "lap trinh", "doi nguoi", "toan hoc",
            "lich su Viet Nam", "tam ly hoc", "an ninh mang", "ky nang mem", "dau tu"];
const AUTHORS = ["Nguyen Nhat Anh", "To Hoai", "Robert C. Martin", "Martin Fowler",
                 "Dale Carnegie", "Peter Lynch", "Yuval Noah Harari", "Erich Gamma",
                 "Malcolm Gladwell", "Adam Grant", "Eckhart Tolle", "Chris Voss"];
const PUBLISHERS = ["NXB Tre", "NXB Kim Dong", "NXB Lao Dong", "NXB Tong Hop",
                    "NXB The Gioi", "NXB Bach Khoa", "NXB Van Hoa"];
const TAG_POOL = ["ban chay", "moi", "giam gia", "kinh dien", "sach hay", "hot",
                  "freeship", "combo", "tieng anh", "sach y", "ebook"];

const books = [];
let bookId = 10001;
for (let i = 0; i < 400; i++) {
  const sellerId = sellerIds[i % sellerIds.length];
  const seller = users.find(u => u._id === sellerId);
  const shop = shops.find(s => s.sellerId === sellerId);
  const cat = categories[rint(0, categories.length - 1)];
  const price = rint(30, 500) * 1000;
  const discount = rnd() < 0.4 ? rint(1, 15) * 1000 : 0;
  const tags = [];
  for (let t = 0; t < rint(1, 3); t++) {
    const tag = pick(TAG_POOL);
    if (tags.indexOf(tag) === -1) tags.push(tag);
  }
  books.push({
    _id: bookId,
    title: pick(T1) + " " + pick(T2) + " " + rint(1, 500),
    author: pick(AUTHORS),
    publisher: pick(PUBLISHERS),
    publishYear: rint(1995, 2025),
    isbn: "978" + String(rint(100000000, 999999999)),
    description: "Mo ta sach " + bookId + ": noi dung chuyen sau, phu hop doc gia Viet Nam.",
    price: price,
    discountAmount: discount,
    finalPrice: price - discount,
    stockQuantity: rint(0, 300),
    images: {
      thumbnail: "/uploads/covers/" + bookId + "_s.jpg",
      medium: "/uploads/covers/" + bookId + "_m.jpg",
      large: "/uploads/covers/" + bookId + "_l.jpg"
    },
    categoryId: cat._id,
    categoryName: cat.name,
    categoryPath: cat.path,
    sellerId: sellerId,
    seller: {
      id: sellerId, username: seller.username,
      shopName: shop ? shop.shopName : "Shop",
      avatarUrl: seller.profile.avatarUrl
    },
    tags: tags,
    rating: { avg: 0, count: 0, distribution: { "1": 0, "2": 0, "3": 0, "4": 0, "5": 0 }, lastReviewAt: null },
    stats: { soldCount: 0, viewCount: rint(0, 50000), wishlistCount: rint(0, 800) },
    topReviews: [],
    boughtTogether: [],
    approvalStatus: rnd() < 0.85 ? "APPROVED" : (rnd() < 0.5 ? "PENDING" : "REJECTED"),
    isActive: rnd() > 0.1,
    isPinned: rnd() < 0.08,
    schemaVersion: 1,
    createdAt: daysAgo(rint(30, 700)),
    updatedAt: daysAgo(rint(0, 20))
  });
  bookId++;
}
db.books.insertMany(books);
print("    inserted books: " + books.length);

// ===========================================================================
// [5/9] COUPONS (collation case-insensitive thay cho LOWER(code) cua SQL)
// ===========================================================================
print("--- [5/9] coupons ---");
const coupons = [];
for (let i = 1; i <= 40; i++) {
  const isSellerVoucher = i > 20;
  const type = rnd() < 0.5 ? "FIXED" : "PERCENT";
  const expired = rnd() < 0.2;
  coupons.push({
    _id: 7000 + i,
    code: (isSellerVoucher ? "SHOP" : "BOOKOM") + i + "K",
    type: type,
    discountValue: type === "FIXED" ? rint(5, 50) * 1000 : rint(5, 30),
    scope: isSellerVoucher ? "SELLER" : "GLOBAL",
    sellerId: isSellerVoucher ? sellerIds[i % sellerIds.length] : null,
    description: "Voucher " + (type === "FIXED" ? "giam tien" : "giam phan tram"),
    minOrderAmount: rint(0, 5) * 50000,
    maxDiscountAmount: type === "PERCENT" ? rint(50, 300) * 1000 : null,
    startDate: daysAgo(rint(30, 150)),
    expiresAt: expired ? daysAgo(rint(1, 20)) : daysAgo(-rint(1, 90)),
    totalQuantity: rnd() < 0.3 ? -1 : rint(50, 1000),
    usedCount: rint(0, 120),
    perUserLimit: rnd() < 0.5 ? 1 : null,
    isActive: rnd() < 0.85,
    createdAt: daysAgo(rint(30, 150)),
    updatedAt: daysAgo(rint(1, 20))
  });
}
db.coupons.insertMany(coupons);
print("    inserted coupons: " + coupons.length);

// ===========================================================================
// [6/9] CARTS (aggregate: 1 document / buyer, items[] embedded)
// ===========================================================================
print("--- [6/9] carts ---");
const APPROVED_BOOKS = books.filter(b => b.approvalStatus === "APPROVED" && b.isActive);
// "Sach hot": tap nho sach hay duoc mua cung nhau -> can thiet de khai thac
// luat ket hop co support/confidence/lift vuot nguong (du lieu random thuan
// se cho lift ~ 1.0 nen khong tao ra duoc luat nao).
const HOT_BOOKS = APPROVED_BOOKS.slice(0, 25);
function pickBasketBook() {
  return rnd() < 0.7
    ? HOT_BOOKS[rint(0, HOT_BOOKS.length - 1)]
    : APPROVED_BOOKS[rint(0, APPROVED_BOOKS.length - 1)];
}
const carts = [];
const cartBuyers = buyerIds.slice(0, 150);
cartBuyers.forEach(function (bid, idx) {
  const used = [];
  const items = [];
  for (let k = 0; k < rint(1, 6); k++) {
    const b = pickBasketBook();
    if (used.indexOf(b._id) !== -1) continue;
    used.push(b._id);
    items.push({
      itemId: b._id, bookId: b._id, sellerId: b.sellerId, sellerName: b.seller.shopName,
      title: b.title, unitPrice: b.finalPrice, imageUrl: b.images.thumbnail,
      quantity: rint(1, 3), addedAt: daysAgo(rint(1, 60))
    });
  }
  const subtotal = items.reduce((s, it) => s + it.unitPrice * it.quantity, 0);
  carts.push({
    _id: 200000 + bid,
    buyerId: bid,
    items: items,
    totals: { subtotal: subtotal, itemCount: items.length },
    createdAt: daysAgo(rint(30, 300)),
    updatedAt: daysAgo(idx % 5 === 0 ? rint(30, 120) : rint(0, 10))   // vd gio bo hoang
  });
});
db.carts.insertMany(carts);
print("    inserted carts: " + carts.length + " (co ca gio bo hoang > 30 ngay)");

// ===========================================================================
// [7/9] ORDERS (aggregate: subOrders[].items[] + shipping + payment + voucher)
//       600 don trai 12 thang, nhieu trang thai, 35% don co 2 seller
// ===========================================================================
print("--- [7/9] orders ---");
const PAY_METHODS = ["COD", "VNPAY", "MOMO", "BANK_TRANSFER"];

function orderStatusFor(days) {
  if (days < 5) return rnd() < 0.5 ? "PROCESSING" : "SHIPPING";
  if (days < 20) return rnd() < 0.4 ? "SHIPPING" : "DELIVERED";
  const r = rnd();
  if (r < 0.62) return "COMPLETED";
  if (r < 0.80) return "CANCELLED";
  if (r < 0.93) return "RETURNED";
  return "DELIVERED";
}
function statusToPayment(st) {
  if (st === "CANCELLED") return "CANCELLED";
  if (st === "PROCESSING" || st === "PENDING") return "PENDING";
  return "SUCCESS";
}
function statusHistoryFor(status, createdAt) {
  const chain = ["PROCESSING", "SHIPPING", "DELIVERED", "COMPLETED"];
  const hist = [{ status: "PROCESSING", at: createdAt, note: "Don hang duoc tao" }];
  for (let i = 1; i < chain.length; i++) {
    if (chain[i] === status || chain.indexOf(status) > i) {
      hist.push({ status: chain[i], at: new Date(createdAt.getTime() + i * 86400000), note: "" });
    }
  }
  if (status === "CANCELLED") {
    hist.push({ status: "CANCELLED", at: new Date(createdAt.getTime() + 86400000), note: "Nguoi mua huy" });
  }
  if (status === "RETURNED") {
    hist.push({ status: "RETURNED", at: new Date(createdAt.getTime() + 5 * 86400000), note: "Tra hang" });
  }
  return hist;
}

const orders = [];
const orderItemsIndex = [];       // chi tiet da mua -> sinh review "verified" + tra hang
let subOrderSeq = 1;
for (let i = 1; i <= 600; i++) {
  const buyerId = buyerIds[rint(0, buyerIds.length - 1)];
  const buyer = users.find(u => u._id === buyerId);
  const createdDays = rint(0, 365);
  const createdAt = daysAgo(createdDays);
  const sellerCount = rnd() < 0.35 ? 2 : 1;
  const chosenSellers = [];
  while (chosenSellers.length < sellerCount) {
    const s = sellerIds[rint(0, sellerIds.length - 1)];
    if (chosenSellers.indexOf(s) === -1) chosenSellers.push(s);
  }
  const subOrders = [];
  let itemsTotal = 0;
  chosenSellers.forEach(function (sid, sIdx) {
    const seller = users.find(u => u._id === sid);
    const shop = shops.find(s => s.sellerId === sid);
    const status = orderStatusFor(createdDays);
    const items = [];
    const used = [];
    let subTotal = 0;
    for (let k = 0; k < rint(1, 4); k++) {
      const b = pickBasketBook();
      if (used.indexOf(b._id) !== -1) continue;
      used.push(b._id);
      const qty = rint(1, 3);
      subTotal += b.finalPrice * qty;
      items.push({
        itemId: b._id, bookId: b._id, title: b.title, imageUrl: b.images.thumbnail,
        unitPrice: b.finalPrice, quantity: qty, returnedQuantity: 0
      });
      orderItemsIndex.push({
        orderId: 300000 + i, subOrderId: 500000 + subOrderSeq, bookId: b._id,
        userId: buyerId, status: status, createdAt: createdAt, unitPrice: b.finalPrice, quantity: qty
      });
    }
    if (items.length === 0) return;
    itemsTotal += subTotal;
    subOrders.push({
      subOrderId: 500000 + subOrderSeq,
      sellerId: sid,
      seller: { id: sid, username: seller.username, shopName: shop ? shop.shopName : "Shop" },
      status: status,
      subTotal: subTotal,
      items: items,
      statusHistory: statusHistoryFor(status, createdAt),
      voucherCode: null,
      shipping: {
        method: "STANDARD", fee: 30000,
        trackingCode: (status === "SHIPPING" || status === "DELIVERED" || status === "RETURNED")
          ? "GH" + i + sIdx : null
      },
      createdAt: createdAt,
      updatedAt: createdAt
    });
    subOrderSeq++;
  });
  if (subOrders.length === 0) continue;

  const voucher = rnd() < 0.3 ? coupons[rint(0, coupons.length - 1)] : null;
  const discount = voucher ? Math.min(Math.round(itemsTotal * 0.1), 100000) : 0;
  const shippingFee = 30000;
  const paymentStatus = statusToPayment(subOrders[0].status);
  const total = itemsTotal - discount + shippingFee;
  orders.push({
    _id: 300000 + i,
    orderCode: "ORD-2026-" + String(300000 + i),
    buyerId: buyerId,
    buyer: {
      id: buyerId, username: buyer.username,
      fullName: buyer.profile.lastName + " " + buyer.profile.firstName,
      email: buyer.email, phone: buyer.phone
    },
    itemsTotal: itemsTotal,
    discountAmount: discount,
    shippingFee: shippingFee,
    totalAmount: total,
    couponCode: voucher ? voucher.code : null,
    voucher: voucher ? { code: voucher.code, type: voucher.type, discountAmount: discount } : null,
    shipping: {
      address: buyer.addresses.length
        ? buyer.addresses[0].addressLine + ", " + buyer.addresses[0].province
        : "Chua cap nhat",
      recipient: buyer.addresses.length ? buyer.addresses[0].recipientName : buyer.username,
      phone: buyer.phone, method: "STANDARD", fee: shippingFee, trackingCode: null
    },
    payment: {
      method: pick(PAY_METHODS), status: paymentStatus, amount: total,
      paidAt: paymentStatus === "SUCCESS" ? createdAt : null
    },
    status: subOrders[0].status,
    subOrders: subOrders,
    isDeleted: false,
    schemaVersion: 1,
    createdAt: createdAt,
    updatedAt: createdAt
  });
}
db.orders.insertMany(orders);
print("    inserted orders: " + orders.length + " | subOrders: " + (subOrderSeq - 1));
print("    chi tiet san pham da ban (nguon sinh review/tra hang): " + orderItemsIndex.length);

// ===========================================================================
// [8/9] REVIEWS (aggregate RIENG: images[] + replies[] + moderation)
//       - Chi sinh review cho don DELIVERED/COMPLETED => isVerifiedPurchase
//       - Dam bao rang buoc unique (bookId, userId)
// ===========================================================================
print("--- [8/9] reviews ---");
const COMMENT_POOL = [
  "Sach rat hay, giao hang nhanh", "Noi dung sach ok, bia hoi mong",
  "Dong goi can than, se mua lai", "Gia tot so voi chat luong",
  "Sach hoi kho doc voi nguoi moi", "Shop tu van nhiet tinh, ship nhanh",
  "In an ro net, giay dep", "Mua sale nen rat dang tien",
  "Noi dung khong nhu mo ta", "Sach cu, mong shop kiem tra ky"
];
const REVIEW_STATUS = ["VISIBLE", "VISIBLE", "VISIBLE", "VISIBLE", "HIDDEN"];
const reviews = [];
const seenPairs = {};
const reviewCandidates = orderItemsIndex.filter(
  it => it.status === "DELIVERED" || it.status === "COMPLETED");

for (let i = 0; i < reviewCandidates.length && reviews.length < 1200; i++) {
  const it = reviewCandidates[i];
  if (rnd() > 0.45) continue;                       // ~45% nguoi mua co danh gia
  const key = it.bookId + "::" + it.userId;
  if (seenPairs[key]) continue;                     // tranh vi pham unique index
  seenPairs[key] = true;

  const book = books.find(b => b._id === it.bookId);
  const user = users.find(u => u._id === it.userId);
  const rating = rnd() < 0.6 ? 5 : (rnd() < 0.7 ? 4 : rint(1, 3));
  const status = pick(REVIEW_STATUS);
  const createdAt = new Date(it.createdAt.getTime() + rint(1, 20) * 86400000);
  const withImages = rnd() < 0.25;
  const replyCount = rnd() < 0.35 ? rint(1, 2) : 0;
  const replies = [];
  for (let r = 0; r < replyCount; r++) {
    replies.push({
      replyId: 9000 + reviews.length * 10 + r,
      userId: it.sellerIdForReply || book.sellerId,
      user: { username: book.seller.username, role: "SELLER" },
      content: "Cam on ban da danh gia, shop se tiep tuc phat huy!",
      createdAt: new Date(createdAt.getTime() + 86400000)
    });
  }
  reviews.push({
    _id: 400000 + reviews.length + 1,
    bookId: it.bookId,
    book: { title: book.title, imageUrl: book.images.thumbnail, sellerId: book.sellerId,
            categoryId: book.categoryId, categoryName: book.categoryName },
    userId: it.userId,
    user: { username: user.username, fullName: user.profile.lastName + " " + user.profile.firstName,
            avatarUrl: user.profile.avatarUrl },
    orderId: it.orderId,
    isVerifiedPurchase: true,
    rating: rating,
    comment: pick(COMMENT_POOL),
    images: withImages
      ? [{ fileId: null, url: "/uploads/reviews/r" + it.bookId + "_1.jpg" },
         { fileId: null, url: "/uploads/reviews/r" + it.bookId + "_2.jpg" }]
      : [],
    replies: replies,
    helpfulCount: rint(0, 120),
    moderation: {
      status: status,
      reason: status === "HIDDEN" ? "Noi dung khong phu hop" : null,
      byUserId: status === "HIDDEN" ? ADMIN_ID : null,
      at: status === "HIDDEN" ? daysAgo(rint(1, 30)) : null,
      history: [{ status: status, byUserId: status === "HIDDEN" ? ADMIN_ID : null,
                  at: status === "HIDDEN" ? daysAgo(rint(1, 30)) : createdAt }]
    },
    flags: { count: rint(0, 3), reasons: rnd() < 0.3 ? ["SPAM"] : [] },
    createdAt: createdAt,
    updatedAt: createdAt,
    schemaVersion: 1
  });
}
db.reviews.insertMany(reviews);
print("    inserted reviews: " + reviews.length);

// ---------------------------------------------------------------------------
// MATERIALIZED VIEW #1: books.rating  <- tinh tu reviews bang $merge
//   Day la "cai dat lai bang du lieu" (eventual consistency co kiem soat):
//   ghi review chi 1 collection, con so tong hop duoc $merge vao books.
// ---------------------------------------------------------------------------
print("    [$merge] Tinh lai books.rating tu reviews ...");
const mergeRating = db.reviews.aggregate([
  { $match: { "moderation.status": "VISIBLE" } },
  {
    $group: {
      _id: "$bookId",
      avg: { $avg: "$rating" },
      count: { $sum: 1 },
      stars: { $push: "$rating" },
      lastReviewAt: { $max: "$createdAt" }
    }
  },
  {
    $project: {
      avg: { $round: ["$avg", 2] },
      count: 1,
      lastReviewAt: 1,
      distribution: {
        $arrayToObject: {
          $map: {
            input: [1, 2, 3, 4, 5],
            as: "star",
            in: {
              k: { $toString: "$$star" },
              v: {
                $size: {
                  $filter: { input: "$stars", as: "r", cond: { $eq: ["$$r", "$$star"] } }
                }
              }
            }
          }
        }
      }
    }
  },
  {
    $merge: {
      into: "books",
      on: "_id",
      whenMatched: [{
        $set: {
          rating: {
            avg: "$$new.avg", count: "$$new.count",
            distribution: "$$new.distribution", lastReviewAt: "$$new.lastReviewAt"
          },
          updatedAt: "$$NOW"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
const ratedCount = db.books.countDocuments({ "rating.count": { $gt: 0 } });
print("    -> so sach da co diem danh gia: " + ratedCount);

// ===========================================================================
// 8b. NOTIFICATIONS + NOTIFICATION_DELIVERIES (queue retry, co TTL)
// ===========================================================================
print("--- notifications / deliveries ---");
const NOTI_TYPES = ["ORDER_CREATED", "ORDER_STATUS_CHANGED", "PAYMENT_SUCCESS",
                    "PROMOTION", "REVIEW_REPLY", "SYSTEM"];
const notifications = [];
const deliveries = [];
let deliverySeq = 1;
for (let i = 1; i <= 300; i++) {
  const userId = pick(buyerIds);
  const isRead = rnd() < 0.55;
  const createdAt = daysAgo(rint(0, 180));
  const type = pick(NOTI_TYPES);
  const notiId = 600000 + i;
  const orderId = pick(orders)._id;
  notifications.push({
    _id: notiId,
    userId: userId,
    type: type,
    title: type === "ORDER_STATUS_CHANGED" ? "Don hang cap nhat trang thai" : "Thong bao moi",
    message: "Ban co thong bao " + type + " cho don hang #" + orderId,
    payload: { orderId: orderId, type: type, deepLink: "/orders/" + orderId },   // BSON doc (khong phai chuoi JSON nhu SQL)
    isRead: isRead,
    priority: pick(["LOW", "NORMAL", "HIGH", "URGENT"]),
    readAt: isRead ? new Date(createdAt.getTime() + 3600000) : null,
    // TTL: thong bao cu > 90 ngay (tinh tu createdAt + 90d) se bi Mongo tu xoa
    expiresAt: new Date(createdAt.getTime() + 90 * 86400000),
    createdAt: createdAt
  });

  // moi thong bao co 1-2 lan gui (SSE + EMAIL), co lan that bai de queue worker retry
  const channels = rnd() < 0.4 ? ["SSE", "EMAIL"] : ["SSE"];
  channels.forEach(function (ch) {
    const failed = rnd() < 0.12;
    const status = failed ? (rnd() < 0.5 ? "PENDING" : "FAILED") : "SENT";
    deliveries.push({
      _id: 650000 + deliverySeq,
      notificationId: notiId,
      userId: userId,
      channel: ch,
      status: status,
      attemptCount: failed ? rint(1, 6) : 1,
      nextRetryAt: status === "PENDING" ? daysAgo(-1 * rint(0, 2)) : null,
      lastError: failed ? "Connection timeout" : null,
      sentAt: status === "SENT" ? new Date(createdAt.getTime() + 2000) : null,
      createdAt: createdAt,
      updatedAt: createdAt
    });
    deliverySeq++;
  });
}
db.notifications.insertMany(notifications);
db.notification_deliveries.insertMany(deliveries);
print("    inserted notifications: " + notifications.length + " | deliveries: " + deliveries.length);

// ===========================================================================
// 8c. PAYMENT_TRANSACTIONS (+ TTL cho link VNPay het han)
// ===========================================================================
print("--- payment_transactions ---");
const payments = [];
orders.forEach(function (o, idx) {
  const paid = o.payment.status;
  const st = paid === "SUCCESS" ? "SUCCESS" : (paid === "CANCELLED" ? "CANCELLED" : "PENDING");
  payments.push({
    _id: 800000 + idx + 1,
    orderId: o._id,
    amount: o.totalAmount,
    method: o.payment.method,
    status: st,
    transactionCode: st === "SUCCESS" ? "VNP" + String(o._id) : null,
    paymentUrl: st === "PENDING" ? "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=" + o.totalAmount : null,
    responseCode: st === "SUCCESS" ? "00" : (st === "PENDING" ? null : "24"),
    responseMessage: st === "SUCCESS" ? "Giao dich thanh cong" : (st === "PENDING" ? null : "Khach hang huy"),
    ipnPayload: st === "SUCCESS"
      ? { vnp_TxnRef: String(o._id), vnp_ResponseCode: "00", vnp_BankCode: "NCB" }
      : null,
    failureReason: st === "CANCELLED" ? "Khach hang huy giao dich" : null,
    createdAt: o.createdAt,
    paidAt: st === "SUCCESS" ? o.createdAt : null,
    expiredAt: o.createdAt
  });
});
db.payment_transactions.insertMany(payments);
print("    inserted payment_transactions: " + payments.length);

// ===========================================================================
// 8d. ORDER_RETURNS + SUPPORT_TICKETS (du lieu cho ML feature return_rate)
// ===========================================================================
print("--- order_returns / support_tickets ---");
const returns = [];
const returnedCandidates = orderItemsIndex.filter(it => it.status === "RETURNED");
returnedCandidates.slice(0, 60).forEach(function (it, idx) {
  returns.push({
    _id: 850000 + idx + 1,
    orderId: it.orderId,
    subOrderId: it.subOrderId,
    orderItemId: it.bookId,
    userId: it.userId,
    quantityReturned: rint(1, it.quantity),
    reason: pick(["DEFECTIVE", "WRONG_ITEM", "CHANGE_MIND", "OTHER"]),
    status: pick(["PENDING", "APPROVED", "REJECTED", "REFUNDED"]),
    itemSnapshot: { bookId: it.bookId, unitPrice: it.unitPrice, quantity: it.quantity },
    createdAt: new Date(it.createdAt.getTime() + 3 * 86400000),
    processedAt: rnd() < 0.6 ? new Date(it.createdAt.getTime() + 7 * 86400000) : null
  });
});
db.order_returns.insertMany(returns);

const tickets = [];
for (let i = 1; i <= 40; i++) {
  const createdAt = daysAgo(rint(0, 300));
  const st = pick(["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"]);
  tickets.push({
    _id: 880000 + i,
    userId: pick(buyerIds),
    subject: "Hoi ve don hang / thanh toan #" + i,
    description: "Khach hang can ho tro ve van de giao hang va hoan tien.",
    status: st,
    priority: pick(["LOW", "NORMAL", "HIGH", "URGENT"]),
    createdAt: createdAt,
    resolvedAt: (st === "RESOLVED" || st === "CLOSED")
      ? new Date(createdAt.getTime() + rint(1, 10) * 86400000) : null
  });
}
db.support_tickets.insertMany(tickets);
print("    inserted order_returns: " + returns.length + " | support_tickets: " + tickets.length);

// ===========================================================================
// [9/9] ACTIVITY_LOG (time-series) + TOKENS + OTP + LOCKS + COUNTERS
// ===========================================================================
print("--- [9/9] activity_log / tokens / otp / locks / counters ---");
const ACTIONS = ["VIEW_BOOK", "SEARCH", "ADD_TO_CART", "REMOVE_FROM_CART", "CHECKOUT",
                 "LOGIN", "LOGOUT", "VIEW_ORDER", "WRITE_REVIEW", "WISHLIST_ADD"];
const DEVICES = ["web/chrome", "web/edge", "mobile/android", "mobile/ios"];
const logs = [];
for (let i = 0; i < 5000; i++) {
  const uid = pick(buyerIds);
  const action = pick(ACTIONS);
  const minutesAgo = rint(0, 30 * 24 * 60);          // 30 ngay gan nhat
  logs.push({
    ts: new Date(Date.now() - minutesAgo * 60000),
    userId: uid,
    sessionId: "s" + rint(1, 900),
    action: action,
    bookId: action === "VIEW_BOOK" || action === "WISHLIST_ADD" || action === "ADD_TO_CART"
      ? pick(APPROVED_BOOKS)._id : null,
    metadata: { device: pick(DEVICES), durationMs: rint(200, 9000) },
    ip: "203.113." + rint(0, 255) + "." + rint(1, 254)
  });
}
db.activity_log.insertMany(logs);
print("    inserted activity_log (time-series): " + logs.length);

// refresh_tokens: co cai con han, co cai het han (TTL se tu don)
const tokens = [];
for (let i = 1; i <= 60; i++) {
  const expired = rnd() < 0.3;
  tokens.push({
    _id: 950000 + i,
    userId: buyerIds[i % buyerIds.length],
    token: "rt_" + i + "_" + rint(100000, 999999),
    expiryDate: expired ? daysAgo(rint(1, 5)) : daysAgo(-rint(1, 7)),
    createdAt: expired ? daysAgo(rint(8, 15)) : daysAgo(rint(0, 3))
  });
}
db.refresh_tokens.insertMany(tokens);

const otps = [];
for (let i = 1; i <= 20; i++) {
  const expired = rnd() < 0.5;
  otps.push({
    _id: 970000 + i,
    email: "buyer" + (101 + i) + "@bookom.vn",
    code: String(rint(100000, 999999)),
    purpose: "REGISTER",
    attempts: rint(0, 2),
    verified: false,
    createdAt: daysAgo(expired ? 1 : 0),
    expiresAt: expired ? daysAgo(1) : daysAgo(0)   // het han sau 5 phut trong thuc te
  });
}
db.otp_codes.insertMany(otps);

db.distributed_locks.insertOne({
  _id: "NOTIFICATION_QUEUE_WORKER",
  holderId: "UNOWNED",
  instanceId: "seed",
  acquiredAt: daysAgo(1),
  heartbeatAt: daysAgo(1),
  expiresAt: daysAgo(0)
});

// --- COUNTERS: giu Long id nhu SQL (counters + findAndModify $inc) ----------
function syncCounter(name, coll) {
  const last = db.getCollection(coll).find({}, { _id: 1 }).sort({ _id: -1 }).limit(1).toArray();
  const maxId = last.length && typeof last[0]._id === "number" ? last[0]._id : 0;
  const seq = maxId + 1000;      // chua khoang trong an toan
  db.counters.updateOne(
    { _id: name },
    { $max: { seq: seq }, $setOnInsert: { createdAt: new Date() } },
    { upsert: true }
  );
  print("    counter " + name + " = " + seq);
}
["users", "books", "carts", "orders", "sub_orders", "reviews",
 "notifications", "notification_deliveries", "payment_transactions",
 "order_returns", "support_tickets", "coupons", "categories",
 "seller_shops", "association_rules", "order_item_ids"].forEach(function (name) {
  const coll = name === "sub_orders" || name === "order_item_ids" ? "orders" : name;
  const last = db.getCollection(coll).find({}, { _id: 1 }).sort({ _id: -1 }).limit(1).toArray();
  const maxId = last.length && typeof last[0]._id === "number" ? last[0]._id : 0;
  const bump = (name === "sub_orders" || name === "order_item_ids") ? 900000 : 1000;
  const seq = maxId + bump;
  db.counters.updateOne({ _id: name },
    { $max: { seq: seq }, $setOnInsert: { createdAt: new Date() } }, { upsert: true });
});
print("    counters da dong bo (xem collection counters)");

// ===========================================================================
// MATERIALIZED VIEW #2: books.stats.soldCount  <- tu orders
// MATERIALIZED VIEW #3: users.stats            <- tu orders
// MATERIALIZED VIEW #4: seller_shops.stats     <- tu orders
// MATERIALIZED VIEW #5: daily_stats            <- tu orders (doanh thu theo ngay)
// ===========================================================================
print("--- [$merge] Tinh lai cac so tong hop tu orders ---");

const orderItemsUnwind = [
  { $match: { isDeleted: { $ne: true } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $nin: ["CANCELLED"] } } },
  { $unwind: "$subOrders.items" }
];

// (2) soldCount theo sach
db.orders.aggregate(orderItemsUnwind.concat([
  {
    $group: {
      _id: "$subOrders.items.bookId",
      sold: { $sum: "$subOrders.items.quantity" },
      revenue: { $sum: { $multiply: ["$subOrders.items.quantity", "$subOrders.items.unitPrice"] } }
    }
  },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{
        $set: {
          "stats.soldCount": "$$new.sold",
          "stats.revenue": "$$new.revenue"
        }
      }],
      whenNotMatched: "discard"
    }
  }
])).toArray();
print("    -> books.stats.soldCount OK");

// (3) users.stats: so don, tong chi tieu, don gan nhat
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true } } },
  {
    $group: {
      _id: "$buyerId",
      orderCount: { $sum: 1 },
      totalSpent: { $sum: "$totalAmount" },
      lastOrderAt: { $max: "$createdAt" }
    }
  },
  {
    $set: {
      avgOrderValue: { $round: [{ $divide: ["$totalSpent", "$orderCount"] }, 0] }
    }
  },
  {
    $merge: {
      into: "users", on: "_id",
      whenMatched: [{
        $set: {
          "stats.orderCount": "$$new.orderCount",
          "stats.totalSpent": "$$new.totalSpent",
          "stats.avgOrderValue": "$$new.avgOrderValue",
          "stats.lastOrderAt": "$$new.lastOrderAt"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    -> users.stats OK");

// (4) seller_shops.stats: so san pham, don hang, doanh thu
db.orders.aggregate(orderItemsUnwind.concat([
  {
    $group: {
      _id: "$subOrders.sellerId",
      orderCount: { $sum: 1 },
      revenue: { $sum: "$subOrders.subTotal" },
      unitsSold: { $sum: "$subOrders.items.quantity" }
    }
  },
  {
    $merge: {
      into: "seller_shops", on: "sellerId",
      whenMatched: [{
        $set: {
          "stats.revenue": "$$new.revenue",
          "stats.unitsSold": "$$new.unitsSold",
          "stats.shippedOrders": "$$new.orderCount",
          updatedAt: "$$NOW"
        }
      }],
      whenNotMatched: "discard"
    }
  }
])).toArray();
print("    -> seller_shops.stats OK");

// (5) daily_stats: doanh thu / so don theo ngay (dung $dateTrunc)
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $ne: "CANCELLED" } } },
  {
    $group: {
      _id: {
        dateKey: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } },
        sellerId: "$subOrders.sellerId"
      },
      revenue: { $sum: "$subOrders.subTotal" },
      orderCount: { $sum: 1 },
      unitsSold: { $sum: { $size: "$subOrders.items" } }
    }
  },
  {
    $project: {
      _id: 0,
      dateKey: "$_id.dateKey",
      sellerId: "$_id.sellerId",
      revenue: 1, orderCount: 1, unitsSold: 1,
      updatedAt: "$$NOW"
    }
  },
  {
    $merge: {
      into: "daily_stats", on: ["dateKey", "sellerId"],
      whenMatched: "replace", whenNotMatched: "insert"
    }
  }
]).toArray();
print("    -> daily_stats OK (" + db.daily_stats.countDocuments({}) + " dong)");

// ---------------------------------------------------------------------------
// SUBSET PATTERN: books.topReviews (3 review moi nhat) - doc nhanh trang chi tiet
// ---------------------------------------------------------------------------
db.reviews.aggregate([
  { $match: { "moderation.status": "VISIBLE" } },
  { $sort: { createdAt: -1 } },
  {
    $group: {
      _id: "$bookId",
      topReviews: {
        $push: {
          reviewId: "$_id", userId: "$userId", userName: "$user.username",
          rating: "$rating", comment: "$comment", createdAt: "$createdAt",
          helpfulCount: "$helpfulCount"
        }
      }
    }
  },
  { $project: { topReviews: { $slice: ["$topReviews", 3] } } },   // CHI 3 phan tu
  { $merge: { into: "books", on: "_id", whenMatched: [{ $set: { topReviews: "$$new.topReviews" } }] } }
]).toArray();
print("    -> books.topReviews (subset 3) OK");

// ===========================================================================
// MATERIALIZED VIEW #6: association_rules  <- KHAI THAC LUAT KET HOP bang
//   aggregation pipeline (thay the thuat toan FP-Growth viet tay cua ban SQL).
//   Y tuong: gop order thanh basket -> $facet (tong so basket, dem theo sach,
//   dem theo cap sach) -> tinh support/confidence/lift.
// ===========================================================================
print("--- [$facet] Khai thac luat ket hop (bought-together) ---");
const mining = db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $in: ["COMPLETED", "DELIVERED"] } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $in: ["COMPLETED", "DELIVERED"] } } },
  { $unwind: "$subOrders.items" },
  { $group: { _id: "$_id", books: { $addToSet: "$subOrders.items.bookId" } } },
  { $match: { "books.1": { $exists: true } } },        // chi gio co >= 2 dau sach
  {
    $facet: {
      total: [{ $count: "n" }],
      bookCounts: [
        { $unwind: "$books" },
        { $group: { _id: "$books", n: { $sum: 1 } } }
      ],
      pairCounts: [
        // LUU Y: phai NHAN BAN mang truoc khi unwind 2 lan, vi $unwind tren
        // scalar khong khoi phuc mang -> neu khong se ra 0 cap.
        { $project: { bookA: "$books", bookB: "$books" } },
        { $unwind: "$bookA" },
        { $unwind: "$bookB" },
        { $match: { $expr: { $lt: ["$bookA", "$bookB"] } } },   // A<B: khong trung lap
        { $group: { _id: { a: "$bookA", b: "$bookB" }, n: { $sum: 1 } } }
      ]
    }
  }
]).toArray()[0];

const totalBaskets = mining.total.length ? mining.total[0].n : 0;
const bookCountMap = {};
mining.bookCounts.forEach(function (r) { bookCountMap[r._id] = r.n; });

const rules = [];
mining.pairCounts.forEach(function (p) {
  const a = p._id.a, b = p._id.b, pairCount = p.n;
  const countA = bookCountMap[a] || 0, countB = bookCountMap[b] || 0;
  if (countA === 0 || countB === 0) return;
  const support = pairCount / totalBaskets;
  const confidence = pairCount / countA;                       // P(B | A)
  const lift = confidence / (countB / totalBaskets);
  if (support >= 0.01 && confidence >= 0.2 && lift > 1.0) {
    // luu ca 2 chieu de tra cuu nhanh (A->B va B->A)
    rules.push({ bookAId: a, bookBId: b, support: Math.round(support * 10000) / 10000,
                 confidence: Math.round(confidence * 10000) / 10000,
                 lift: Math.round(lift * 10000) / 10000,
                 transactionCount: pairCount, windowDays: 365, updatedAt: new Date() });
    rules.push({ bookAId: b, bookBId: a, support: Math.round(support * 10000) / 10000,
                 confidence: Math.round(pairCount / countB * 10000) / 10000,
                 lift: Math.round((pairCount / countB) / (countA / totalBaskets) * 10000) / 10000,
                 transactionCount: pairCount, windowDays: 365, updatedAt: new Date() });
  }
});
if (rules.length > 0) {
  db.association_rules.insertMany(rules, { ordered: false });
}
print("    baskets=" + totalBaskets + " | cap sach=" + mining.pairCounts.length +
      " | luat ket hop=" + rules.length);

// ---------------------------------------------------------------------------
// SUBSET PATTERN: books.boughtTogether (top 10 goi y cho moi sach)
// ---------------------------------------------------------------------------
db.association_rules.aggregate([
  { $sort: { confidence: -1, lift: -1 } },
  {
    $group: {
      _id: "$bookAId",
      items: {
        $push: {
          bookId: "$bookBId", confidence: "$confidence",
          lift: "$lift", support: "$support"
        }
      }
    }
  },
  { $project: { items: { $slice: ["$items", 10] } } },        // chi 10 goi y
  { $merge: { into: "books", on: "_id", whenMatched: [{ $set: { boughtTogether: "$$new.items" } }] } }
]).toArray();
print("    -> books.boughtTogether (top 10) OK");

// ===========================================================================
// TONG KET
// ===========================================================================
print("============================================================");
print("TONG KET DU LIEU SEED");
print("============================================================");
db.getCollectionNames().sort().forEach(function (c) {
  if (c.indexOf("system.") === 0) return;
  print("  " + c + ": " + db.getCollection(c).countDocuments({}) + " document");
});
const stats = db.stats(1024 * 1024);
print("------------------------------------------------------------");
print("dataSize: " + stats.dataSize + " MB | storageSize: " + stats.storageSize + " MB");
print("=== HOAN TAT 03_seed_reference.js ===");
