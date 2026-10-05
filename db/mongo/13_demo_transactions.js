// ============================================================================
// 13_demo_transactions.js - SINH DU LIEU GIAO DICH THAT cho demo / bao cao
//
//   Dieu kien:
//     - `books` da import tu Books.csv   (tools\mongo-import-books.bat)
//     - 100 nha ban that (role SELLER)   (tools\mongo-import-sellers.bat)
//     - tai khoan BUYER that             (tools\mongo-demo-accounts.bat)
//
//   Script se XOA roi SINH LAI (chi cac collection giao dich):
//     carts, orders, reviews, coupons, payment_transactions, order_returns,
//     support_tickets, notifications, notification_deliveries, daily_stats,
//     association_rules, activity_log (time-series)
//   va tinh lai cac MATERIALIZED VIEW bang $merge:
//     books.rating / books.topReviews / books.stats / books.tags,
//     users.stats / users.ml (RFM), seller_shops.stats, categories.bookCount,
//     books.boughtTogether  (+ dong bo collection counters)
//
//   Chay: tools\mongo-demo-data.bat
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);

const NOW = new Date();
const DAY = 86400000;
const SEED = 20261004;            // co dinh -> chay lai ra cung bo du lieu

// --- PRNG co seed (xorshift) -> du lieu on dinh giua cac lan chay ----------
let _s = SEED >>> 0;
function rnd() {
  _s ^= _s << 13; _s >>>= 0;
  _s ^= _s >>> 17;
  _s ^= _s << 5;  _s >>>= 0;
  return _s / 4294967296;
}
function rint(a, b) { return a + Math.floor(rnd() * (b - a + 1)); }
function pick(arr) { return arr[Math.floor(rnd() * arr.length)]; }
function daysAgo(d) { return new Date(NOW.getTime() - d * DAY); }
function L(v) { return NumberLong(String(v)); }
function I(v) { return NumberInt(v); }
function D(v) { return Double(v); }
function money(v) { return Math.round(v / 1000) * 1000; }
function title(t) { print("\n----- " + t + " -----"); }
function insertBatched(collName, docs, size) {
  const coll = db.getCollection(collName);
  for (let i = 0; i < docs.length; i += size) {
    coll.insertMany(docs.slice(i, i + size), { ordered: false });
  }
  return docs.length;
}

print("==============================================================");
print(" 13_demo_transactions.js | DB = " + db.getName());
print("   books=" + db.books.countDocuments({}) +
  " | seller=" + db.users.countDocuments({ role: "SELLER" }) +
  " | buyer=" + db.users.countDocuments({ role: "BUYER" }) +
  " | shop=" + db.seller_shops.countDocuments({}));
print("==============================================================");

// ===========================================================================
// [1] NAP DU LIEU THAM CHIEU THAT
// ===========================================================================
const buyers = db.users.find({ role: "BUYER" }).sort({ _id: 1 }).toArray();
const sellers = db.users.find({ role: "SELLER" }).sort({ _id: 1 }).toArray();
const shopBySeller = {};
db.seller_shops.find({}).forEach(function (s) { shopBySeller[String(s.sellerId)] = s; });

if (buyers.length === 0) {
  throw new Error("Chua co tai khoan BUYER that - chay tools\\mongo-demo-accounts.bat truoc");
}
if (sellers.length === 0) {
  throw new Error("Chua co SELLER that - chay tools\\mongo-import-sellers.bat truoc");
}
print("\n[1] buyer that = " + buyers.length + " | seller that = " + sellers.length);

// Xoa du lieu giao dich cu (script idempotent: chay lai luon ra ket qua nhu nhau)
title("[2] Xoa du lieu giao dich cu");
["carts", "orders", "reviews", "coupons", "payment_transactions", "order_returns",
  "support_tickets", "notifications", "notification_deliveries", "daily_stats",
  "association_rules", "activity_log"].forEach(function (c) {
    const r = db.getCollection(c).deleteMany({});
    print("    xoa " + (c + "                          ").substring(0, 26) + ": " + r.deletedCount);
  });

// ---------------------------------------------------------------------------
// POOL SACH THEO TUNG NHA BAN
//   Dung index idx_books_seller_status {sellerId, approvalStatus, createdAt}
//   -> lay toi da 40 sach that cho moi nha ban; 30 sach dau cua 30 nha ban
//      dau tien duoc coi la "sach hot" de tao LUAT KET HOP that.
// ---------------------------------------------------------------------------
title("[3] Nap pool sach theo nha ban");
const poolBySeller = {};
const hotBooks = [];
const ALL_BOOKS = [];
sellers.forEach(function (s, idx) {
  const list = db.books.find(
    { sellerId: s._id, approvalStatus: "APPROVED", isActive: true },
    { title: 1, author: 1, finalPrice: 1, images: 1, sellerId: 1, categoryId: 1, categoryName: 1 }
  ).sort({ _id: 1 }).limit(40).toArray();
  poolBySeller[String(s._id)] = list;
  list.forEach(function (b) { ALL_BOOKS.push(b); });
  if (list.length > 0 && idx < 30) { hotBooks.push(list[0]); }
});
print("    pool = " + ALL_BOOKS.length + " sach | sach hot = " + hotBooks.length +
  " | seller co sach = " + Object.keys(poolBySeller).filter(function (k) {
    return poolBySeller[k].length > 0;
  }).length);

function pickBasketBook(sellerId) {
  // 70% chon tu tap "sach hot" (de xuat hien cap mua cung nhau), con lai lay
  // sach that cua chinh nha ban do
  if (sellerId !== undefined && rnd() < 0.55) {
    const pool = poolBySeller[String(sellerId)];
    return pool.length ? pool[rint(0, pool.length - 1)] : pick(ALL_BOOKS);
  }
  return rnd() < 0.7 ? pick(hotBooks) : pick(ALL_BOOKS);
}

// ===========================================================================
// [4] HO SO / DIA CHI THAT CHO BUYER  (de don hang co dia chi giao that,
//     va de cover truy van "dia chi theo tinh thanh")
// ===========================================================================
title("[4] Cap nhat ho so + dia chi cho buyer");
const PROVINCES = ["Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ",
  "Thừa Thiên Huế", "Khánh Hòa", "Lâm Đồng", "Nghệ An", "Đồng Nai"];
const DISTRICTS = ["Quận 1", "Quận 3", "Quận Hải Châu", "Quận Bắc Từ Liêm",
  "TP. Thủ Đức", "Quận Ninh Kiều", "TP. Huế", "TP. Nha Trang", "TP. Đà Lạt", "TP. Biên Hòa"];
const WARDS = ["Phường Cửa Nam", "Phường Bến Nghé", "Phường Thạch Thang",
  "Phường Cổ Nhuế 1", "Phường Hiệp Phú", "Phường An Hòa", "Phường Vĩnh Nghiêm"];
const LAST_NAMES = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ"];
const FIRST_NAMES = ["An", "Bình", "Châu", "Dũng", "Giang", "Hà", "Khánh", "Linh",
  "Minh", "Nam", "Oanh", "Phúc", "Quỳnh", "Sơn", "Trang", "Vy"];
const STREETS = ["Đường Sách", "Đường Nguyễn Huệ", "Đường Lê Lợi", "Phố Bà Triệu",
  "Đường Trần Phú", "Đường Hai Bà Trưng", "Đường Phan Chu Trinh"];

const buyerMeta = {};
buyers.forEach(function (u, i) {
  const lastName = LAST_NAMES[i % LAST_NAMES.length];
  const firstName = FIRST_NAMES[(i * 3 + 1) % FIRST_NAMES.length];
  const fullName = lastName + " " + firstName;
  const phone = "098" + ("000000" + (i + 1)).slice(-7);      // 0980000001 ... (duy nhat)
  const province = PROVINCES[i % PROVINCES.length];
  const district = DISTRICTS[i % DISTRICTS.length];
  const ward = WARDS[i % WARDS.length];
  const addressLine = rint(1, 299) + " " + STREETS[i % STREETS.length];

  // wishlist: 3-8 sach hot (de co du lieu cho "khach thich gi")
  const wish = [];
  for (let k = 0; k < rint(3, 8); k++) { wish.push(pick(ALL_BOOKS)._id); }

  db.users.updateOne(
    { _id: u._id },
    {
      $set: {
        phone: phone,
        email: u.email,
        profile: {
          firstName: firstName, lastName: lastName, fullName: fullName,
          avatarUrl: "/images/avatars/u" + u._id + ".png",
          gender: (i % 2 === 0) ? "MALE" : "FEMALE",
          dateOfBirth: daysAgo(rint(22, 45) * 365)
        },
        addresses: [{
          addressId: 1, recipientName: fullName, phone: phone,
          addressLine: addressLine, ward: ward, district: district, province: province,
          isDefault: true, createdAt: daysAgo(rint(30, 300))
        }],
        wishlist: { bookIds: wish, updatedAt: daysAgo(rint(0, 20)) },
        updatedAt: NOW
      }
    }
  );
  buyerMeta[String(u._id)] = {
    id: u._id, username: u.username, fullName: fullName, email: u.email, phone: phone,
    address: addressLine + ", " + ward + ", " + district + ", " + province, province: province
  };
});
print("    da cap nhat ho so + " + buyers.length + " dia chi that (10 tinh thanh)");

// ===========================================================================
// [5] KE HOACH DON HANG THEO "CHAN DUNG KHACH" (de phan khuc RFM co y nghia)
//     VIP   : mua nhieu, gan day       -> TRUNG_THANH / VIP
//     LOYAL : mua deu                   -> TRUNG_THANH
//     LAPSED: chi mua tu lau (>180 ngay) -> NGUY_CO_ROI
//     NEW   : moi mua 1-2 don           -> MOI / CAN_CHAM_SOC
// ===========================================================================
title("[5] Chia chan dung khach hang");
const plan = [];
buyers.forEach(function (u, i) {
  const n = buyers.length;
  let kind;
  if (i < Math.max(2, Math.round(n * 0.25))) { kind = "VIP"; }
  else if (i < Math.round(n * 0.6)) { kind = "LOYAL"; }
  else if (i < Math.round(n * 0.85)) { kind = "LAPSED"; }
  else { kind = "NEW"; }

  let orderCount, from, to;
  if (kind === "VIP") { orderCount = rint(36, 48); from = 0; to = 45; }
  else if (kind === "LOYAL") { orderCount = rint(26, 34); from = 0; to = 100; }
  else if (kind === "LAPSED") { orderCount = rint(16, 22); from = 170; to = 360; }
  else { orderCount = rint(4, 6); from = 0; to = 20; }

  plan.push({ buyer: u, kind: kind, orderCount: orderCount, from: from, to: to });
});
const totalPlanned = plan.reduce(function (s, p) { return s + p.orderCount; }, 0);
plan.forEach(function (p) {
  print("    " + (buyerMeta[String(p.buyer._id)].username + "                       ").substring(0, 30) +
    " " + (p.kind + "       ").substring(0, 8) + " don=" + p.orderCount);
});
print("    tong don du kien = " + totalPlanned);

// ===========================================================================
// [6] COUPONS (uu dai THAT: 20 voucher san + 20 voucher cua nha ban)
// ===========================================================================
title("[6] Coupons (voucher)");
const coupons = [];
for (let i = 1; i <= 40; i++) {
  const isSellerVoucher = i > 20;
  const type = (i % 2 === 0) ? "PERCENT" : "FIXED";
  const expired = i % 9 === 0;
  const seller = pick(sellers);
  coupons.push({
    _id: L(7000 + i),
    code: (isSellerVoucher ? "SHOP" : "BOOKOM") + i + "K",
    type: type,
    discountValue: type === "FIXED" ? money(rint(10, 50) * 1000) : I(rint(5, 30)),
    scope: isSellerVoucher ? "SELLER" : "GLOBAL",
    sellerId: isSellerVoucher ? seller._id : null,
    description: "Voucher " + (type === "FIXED" ? "giam tien" : "giam phan tram") +
      (isSellerVoucher ? " cho shop " + ((shopBySeller[String(seller._id)] || {}).shopName || seller.username) : " toan san"),
    minOrderAmount: money(rint(0, 4) * 50000),
    maxDiscountAmount: type === "PERCENT" ? money(rint(50, 300) * 1000) : null,
    startDate: daysAgo(rint(30, 150)),
    expiresAt: expired ? daysAgo(rint(1, 20)) : daysAgo(-rint(1, 90)),
    totalQuantity: (i % 3 === 0) ? L(-1) : L(rint(50, 1000)),
    usedCount: L(rint(0, 120)),
    perUserLimit: (i % 2 === 0) ? I(1) : null,
    isActive: !expired,
    createdAt: daysAgo(rint(30, 150)),
    updatedAt: daysAgo(rint(1, 20))
  });
}
insertBatched("coupons", coupons, 40);
const GLOBAL_COUPONS = coupons.filter(function (c) {
  return c.scope === "GLOBAL" && c.isActive;
});
print("    coupons = " + coupons.length + " (GLOBAL dang bat = " + GLOBAL_COUPONS.length + ")");

// ===========================================================================
// [7] CARTS (aggregate root: 1 document / buyer, items[] embedded)
//     ~1/4 so gio la "gio bo hoang" (updatedAt > 30 ngay) -> co chi so KPI
// ===========================================================================
title("[7] Gio hang moi buyer");
let cartItemSeq = 2000000;
const carts = [];
let abandonedCarts = 0;
buyers.forEach(function (u, idx) {
  const items = [];
  const used = [];
  for (let k = 0; k < rint(1, 6); k++) {
    const b = pickBasketBook();
    if (!b || used.indexOf(String(b._id)) !== -1) { continue; }
    used.push(String(b._id));
    const shop = shopBySeller[String(b.sellerId)] || {};
    items.push({
      itemId: L(++cartItemSeq),
      bookId: b._id,
      sellerId: b.sellerId,
      sellerName: shop.shopName || "Bookom",
      title: b.title,
      unitPrice: b.finalPrice,
      imageUrl: (b.images || {}).thumbnail || null,
      quantity: I(rint(1, 3)),
      addedAt: daysAgo(rint(1, 60))
    });
  }
  if (items.length === 0) { return; }
  const subtotal = items.reduce(function (s, it) { return s + it.unitPrice * it.quantity; }, 0);
  const quantity = items.reduce(function (s, it) { return s + it.quantity; }, 0);
  const idleDays = (idx % 4 === 0) ? rint(31, 120) : rint(0, 10);
  if (idleDays > 30) { abandonedCarts++; }
  carts.push({
    _id: L(400000 + idx + 1),
    buyerId: u._id,
    items: items,
    totals: { subtotal: money(subtotal), itemCount: I(items.length), quantity: I(quantity) },
    createdAt: daysAgo(rint(30, 300)),
    updatedAt: daysAgo(idleDays)
  });
});
insertBatched("carts", carts, 50);
print("    carts = " + carts.length + " (bo hoang > 30 ngay = " + abandonedCarts + ")");

// ---------------------------------------------------------------------------
// [7b] Bo sung favoriteCategoryIds cho buyer (du phong truong hop dang ky qua
//      API khong truyen vao) - dung danh muc THAT trong collection categories
// ---------------------------------------------------------------------------
const realCats = db.categories.find({}, { _id: 1, name: 1 }).limit(20).toArray();
if (realCats.length > 0) {
  buyers.forEach(function (u, i) {
    const ids = [];
    for (let k = 0; k < rint(2, 4); k++) { ids.push(realCats[(i * 3 + k) % realCats.length]._id); }
    const uniq = [];
    ids.forEach(function (id) { if (uniq.map(String).indexOf(String(id)) === -1) { uniq.push(id); } });
    db.users.updateOne({ _id: u._id }, { $set: { favoriteCategoryIds: uniq } });
  });
  print("    da gan favoriteCategoryIds cho " + buyers.length + " buyer");
}

// ===========================================================================
// [8] ORDERS (aggregate root: subOrders[].items[] + shipping + payment + voucher)
//     - Trai deu 12 thang; ~40% don co >= 2 nha ban (1 don = nhieu subOrder)
//     - Trang thai theo do "gia" cua don: don moi -> PROCESSING/SHIPPING
// ===========================================================================
title("[8] Don hang (orders + subOrders + items)");
const PAY_METHODS = ["COD", "VNPAY", "MOMO", "BANK_TRANSFER"];
const SHIP_METHODS = ["STANDARD", "EXPRESS", "FAST"];
const SHIP_FEE = { STANDARD: 30000, EXPRESS: 45000, FAST: 60000 };
const CHAIN_MAP = {
  PROCESSING: ["PROCESSING"],
  SHIPPING: ["PROCESSING", "SHIPPING"],
  DELIVERED: ["PROCESSING", "SHIPPING", "DELIVERED"],
  COMPLETED: ["PROCESSING", "SHIPPING", "DELIVERED", "COMPLETED"],
  CANCELLED: ["PROCESSING", "CANCELLED"],
  RETURNED: ["PROCESSING", "SHIPPING", "DELIVERED", "RETURNED"]
};
const NOTE_MAP = {
  PROCESSING: "Don hang duoc tao", SHIPPING: "Da ban giao don vi van chuyen",
  DELIVERED: "Giao hang thanh cong", COMPLETED: "Nguoi mua da nhan hang",
  CANCELLED: "Don hang bi huy", RETURNED: "Nguoi mua tra hang"
};

function orderStatusFor(days) {
  if (days < 5) { return rnd() < 0.6 ? "PROCESSING" : "SHIPPING"; }
  if (days < 20) { return rnd() < 0.45 ? "SHIPPING" : "DELIVERED"; }
  const r = rnd();
  if (r < 0.68) { return "COMPLETED"; }
  if (r < 0.84) { return "CANCELLED"; }
  if (r < 0.95) { return "RETURNED"; }
  return "DELIVERED";
}
function paymentStatusOf(status) {
  if (status === "CANCELLED") { return "CANCELLED"; }
  if (status === "RETURNED") { return "REFUNDED"; }
  if (status === "PROCESSING") { return "PENDING"; }
  return "SUCCESS";
}
function statusHistoryFor(status, createdAt) {
  const chain = CHAIN_MAP[status] || ["PROCESSING"];
  return chain.map(function (st, i) {
    return {
      status: st,
      at: new Date(createdAt.getTime() + i * rint(1, 2) * DAY),
      byUserId: null,
      note: NOTE_MAP[st] || ""
    };
  });
}

// Nha ban "lon": 25 nha ban dau duoc uu tien 55% so lan chon -> doanh thu tap trung
const sellableSellers = sellers.filter(function (s) {
  return (poolBySeller[String(s._id)] || []).length > 0;
});
const bigSellers = sellableSellers.slice(0, 25);
function pickSeller() {
  if (bigSellers.length > 0 && rnd() < 0.55) { return pick(bigSellers); }
  return pick(sellableSellers);
}

let orderSeq = 300000;        // _id don hang bat dau tu 300001
let subOrderSeq = 500000;     // _id subOrder bat dau tu 500001
let orderItemSeq = 1000000;   // itemId bat dau tu 1000001
const orders = [];
const orderItemsIndex = [];   // chi tiet da mua -> nguon sinh review / tra hang / ML
const statusCount = {};

plan.forEach(function (p) {
  const meta = buyerMeta[String(p.buyer._id)];
  for (let n = 0; n < p.orderCount; n++) {
    const createdDays = rint(p.from, p.to);
    const createdAt = daysAgo(createdDays);
    const orderId = L(orderSeq + 1);

    // 1 -> 2 -> (thinh thoang) 3 nha ban khac nhau trong cung 1 don
    let sellerCount = 1;
    if (rnd() < 0.4) { sellerCount = (rnd() < 0.25) ? 3 : 2; }
    const chosen = [];
    let guard = 0;
    while (chosen.length < sellerCount && guard < 20) {
      guard++;
      const s = pickSeller();
      if (chosen.map(function (x) { return String(x._id); }).indexOf(String(s._id)) === -1) {
        chosen.push(s);
      }
    }

    const shipMethod = pick(SHIP_METHODS);
    const shippingFee = SHIP_FEE[shipMethod];
    const subOrders = [];
    let itemsTotal = 0;

    chosen.forEach(function (s, sIdx) {
      const status = orderStatusFor(createdDays);
      const items = [];
      const usedIds = [];
      let subTotal = 0;
      for (let k = 0; k < rint(1, 4); k++) {
        const b = pickBasketBook(s._id);
        if (!b || usedIds.indexOf(String(b._id)) !== -1) { continue; }
        usedIds.push(String(b._id));
        const qty = rint(1, 3);
        const itemId = L(++orderItemSeq);
        subTotal += b.finalPrice * qty;
        items.push({
          itemId: itemId,
          bookId: b._id,
          title: b.title,
          imageUrl: (b.images || {}).thumbnail || null,
          unitPrice: b.finalPrice,
          quantity: I(qty),
          returnedQuantity: I(status === "RETURNED" && k === 0 ? rint(1, qty) : 0)
        });
        orderItemsIndex.push({
          orderId: orderId, subOrderId: L(subOrderSeq + 1), itemId: itemId, bookId: b._id,
          userId: p.buyer._id, sellerId: s._id, status: status, createdAt: createdAt,
          unitPrice: b.finalPrice, quantity: qty
        });
      }
      if (items.length === 0) { return; }
      itemsTotal += subTotal;
      const shop = shopBySeller[String(s._id)] || {};
      const tracked = (status === "SHIPPING" || status === "DELIVERED" ||
        status === "COMPLETED" || status === "RETURNED");
      subOrders.push({
        subOrderId: L(subOrderSeq + 1),
        sellerId: s._id,
        seller: {
          id: s._id, username: s.username, shopName: shop.shopName || "Bookom",
          avatarUrl: ((s.profile || {}).avatarUrl) || null
        },
        status: status,
        subTotal: money(subTotal),
        items: items,
        statusHistory: statusHistoryFor(status, createdAt),
        voucherCode: null,
        shipping: {
          method: shipMethod, fee: shippingFee,
          trackingCode: tracked ? ("GH" + (orderSeq + 1) + sIdx) : null
        },
        createdAt: createdAt,
        updatedAt: createdAt
      });
      subOrderSeq++;
      statusCount[status] = (statusCount[status] || 0) + 1;
    });

    if (subOrders.length === 0) { orderSeq++; continue; }

    // Mien phi van chuyen cho don >= 500.000d
    let shipFeeFinal = shippingFee;
    if (itemsTotal >= 500000) { shipFeeFinal = 0; }

    const voucher = (GLOBAL_COUPONS.length > 0 && rnd() < 0.3) ? pick(GLOBAL_COUPONS) : null;
    let discount = 0;
    if (voucher) {
      if (voucher.type === "FIXED") {
        discount = Math.min(voucher.discountValue, itemsTotal);
      } else {
        discount = Math.min(Math.round(itemsTotal * voucher.discountValue / 100),
          voucher.maxDiscountAmount || itemsTotal);
      }
      discount = money(Math.max(0, discount));
    }

    const total = money(Math.max(0, itemsTotal - discount + shipFeeFinal));
    const payMethod = pick(PAY_METHODS);
    const aggStatus = subOrders[0].status;   // trang thai tong hop cua don

    orders.push({
      _id: orderId,
      orderCode: "ORD-2026-" + (orderSeq + 1),
      buyerId: p.buyer._id,
      buyer: {
        id: p.buyer._id, username: meta.username, fullName: meta.fullName,
        email: meta.email, phone: meta.phone
      },
      itemsTotal: money(itemsTotal),
      discountAmount: discount,
      shippingFee: shipFeeFinal,
      totalAmount: total,
      couponCode: voucher ? voucher.code : null,
      voucher: voucher ? { code: voucher.code, type: voucher.type, discountAmount: discount } : null,
      shipping: {
        recipientName: meta.fullName, phone: meta.phone, address: meta.address,
        province: meta.province, method: shipMethod, fee: shipFeeFinal,
        trackingCode: subOrders[0].shipping.trackingCode
      },
      payment: {
        method: payMethod, status: paymentStatusOf(aggStatus), amount: total,
        paidAt: paymentStatusOf(aggStatus) === "SUCCESS" ? createdAt : null
      },
      status: aggStatus,
      subOrders: subOrders,
      isDeleted: false,
      schemaVersion: I(1),
      createdAt: createdAt,
      updatedAt: new Date(createdAt.getTime() + rint(1, 5) * DAY)
    });
    orderSeq++;
  }
});

insertBatched("orders", orders, 100);
const subOrderCount = orders.reduce(function (s, o) { return s + o.subOrders.length; }, 0);
print("    orders = " + orders.length + " | subOrders = " + subOrderCount +
  " | dong san pham = " + orderItemsIndex.length);
print("    counter sub_orders = " + subOrderSeq + " | counter order_item_ids = " + orderItemSeq);
print("    trang thai subOrder: " + JSON.stringify(statusCount));

// ===========================================================================
// [9] PAYMENT_TRANSACTIONS (moi don >= 1 giao dich; them giao dich FAILED
//     truoc khi thanh cong -> demo "thu lai thanh toan")
// ===========================================================================
title("[9] payment_transactions");
let paymentSeq = 800000;
const payments = [];
orders.forEach(function (o) {
  const st = o.payment.status;
  if (o.payment.method !== "COD" && rnd() < 0.12) {
    paymentSeq++;
    payments.push({
      _id: L(paymentSeq), orderId: o._id, amount: o.totalAmount, method: o.payment.method,
      status: "FAILED", transactionCode: null,
      paymentUrl: "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=" + (o.totalAmount * 100),
      responseCode: "24", responseMessage: "Giao dich that bai do het thoi gian cho",
      ipnPayload: null, failureReason: "Qua thoi gian thanh toan (timeout)",
      createdAt: o.createdAt, paidAt: null,
      expiredAt: new Date(o.createdAt.getTime() + 15 * 60000)
    });
  }
  paymentSeq++;
  payments.push({
    _id: L(paymentSeq),
    orderId: o._id,
    amount: o.totalAmount,
    method: o.payment.method,
    status: st,
    transactionCode: (st === "SUCCESS" || st === "REFUNDED") ? ("VNP" + o._id) : null,
    paymentUrl: st === "PENDING"
      ? "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=" + (o.totalAmount * 100)
      : null,
    responseCode: st === "SUCCESS" ? "00" : (st === "PENDING" ? null : "24"),
    responseMessage: st === "SUCCESS" ? "Giao dich thanh cong"
      : (st === "PENDING" ? null : "Khach hang huy giao dich"),
    ipnPayload: (st === "SUCCESS" || st === "REFUNDED")
      ? { vnp_TxnRef: String(o._id), vnp_ResponseCode: "00", vnp_BankCode: "NCB", vnp_Amount: o.totalAmount * 100 }
      : null,
    failureReason: st === "CANCELLED" ? "Khach hang huy giao dich"
      : (st === "REFUNDED" ? "Hoan tien do nguoi mua tra hang" : null),
    createdAt: o.createdAt,
    paidAt: (st === "SUCCESS" || st === "REFUNDED") ? o.createdAt : null,
    expiredAt: new Date(o.createdAt.getTime() + (st === "PENDING" ? 15 * 60000 : DAY))
  });
});
insertBatched("payment_transactions", payments, 200);
print("    payments = " + payments.length + " (gom " +
  payments.filter(function (p) { return p.status === "FAILED"; }).length + " giao dich FAILED)");

// ===========================================================================
// [10] ORDER_RETURNS (tra hang that, gan voi item da mua)
// ===========================================================================
title("[10] order_returns");
const returns = [];
let returnSeq = 850000;
orderItemsIndex.filter(function (it) { return it.status === "RETURNED"; })
  .slice(0, 120).forEach(function (it) {
    const st = pick(["PENDING", "APPROVED", "REJECTED", "REFUNDED"]);
    const createdAt = new Date(it.createdAt.getTime() + 3 * DAY);
    returnSeq++;
    returns.push({
      _id: L(returnSeq),
      orderId: it.orderId,
      subOrderId: it.subOrderId,
      orderItemId: it.itemId,
      userId: it.userId,
      quantityReturned: I(rint(1, it.quantity)),
      reason: pick(["DEFECTIVE", "WRONG_ITEM", "CHANGE_MIND", "OTHER"]),
      status: st,
      itemSnapshot: {
        bookId: it.bookId, unitPrice: it.unitPrice, quantity: it.quantity,
        lineTotal: money(it.unitPrice * it.quantity)
      },
      createdAt: createdAt,
      processedAt: (st === "PENDING") ? null : new Date(createdAt.getTime() + rint(1, 7) * DAY)
    });
  });
insertBatched("order_returns", returns, 100);
print("    order_returns = " + returns.length);

// ===========================================================================
// [11] SUPPORT_TICKETS (ho tro khach hang)
// ===========================================================================
title("[11] support_tickets");
const TICKET_SUBJECTS = [
  "Hoi ve don hang chua duoc giao", "Yeu cau hoan tien don hang",
  "Khach hang chua nhan duoc hang", "Sach bi loi in / thieu trang",
  "Muon doi dia chi giao hang", "Khong ap dung duoc ma giam gia",
  "Hoi ve quy trinh tra hang", "Khong dang nhap duoc tai khoan"
];
const tickets = [];
for (let i = 1; i <= 60; i++) {
  const createdAt = daysAgo(rint(0, 300));
  const st = pick(["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"]);
  tickets.push({
    _id: L(880000 + i),
    userId: pick(buyers)._id,
    subject: pick(TICKET_SUBJECTS) + " (#" + rint(10000, 99999) + ")",
    description: "Khach hang can duoc ho tro, vui long phan hoi trong 24h.",
    status: st,
    priority: pick(["LOW", "NORMAL", "HIGH", "URGENT"]),
    createdAt: createdAt,
    resolvedAt: (st === "RESOLVED" || st === "CLOSED")
      ? new Date(createdAt.getTime() + rint(1, 10) * DAY) : null
  });
}
insertBatched("support_tickets", tickets, 60);
print("    support_tickets = " + tickets.length +
  " (dang mo = " + tickets.filter(function (t) { return t.status === "OPEN"; }).length + ")");

// ===========================================================================
// [12] REVIEWS (aggregate RIENG: images[] + replies[] + moderation)
//      - Chi review cho dong hang thuoc subOrder DELIVERED / COMPLETED
//        => isVerifiedPurchase = true (da mua that)
//      - Rang buoc UNIQUE uq_reviews_book_user {bookId,userId} o MUC DB
//        => phai loai trung ngay trong script
// ===========================================================================
title("[12] Reviews");
const bookById = {};
ALL_BOOKS.forEach(function (b) { bookById[String(b._id)] = b; });
const sellersById = {};
sellers.forEach(function (s) { sellersById[String(s._id)] = s; });
const adminUser = db.users.findOne({ role: "ADMIN" });

const COMMENT_POOL = {
  5: ["Sach rat hay, giao hang nhanh", "Dong goi can than, giay dep, se mua lai",
    "Shop tu van nhiet tinh, ship nhanh trong ngay", "In an ro net, gia tot so voi chat luong",
    "Mua sale nen rat dang tien"],
  4: ["Noi dung sach ok, bia hoi mong", "Giao hoi cham nhung sach van nguyen ven",
    "Sach hay nhung gia hoi cao", "Chat luong tot, se gioi thieu ban be"],
  3: ["Sach tam duoc, khong nhu ky vong", "Noi dung on, nguon goc ro rang",
    "Gia hop ly nhung dong goi chua chac chan"],
  2: ["Sach hoi cu, mong shop kiem tra ky", "Giao sai phien ban, hoi that vong"],
  1: ["Noi dung khong nhu mo ta", "Sach bi loi in, rat that vong"]
};
const REVIEW_STATUS = ["VISIBLE", "VISIBLE", "VISIBLE", "VISIBLE", "VISIBLE",
  "VISIBLE", "VISIBLE", "VISIBLE", "VISIBLE", "HIDDEN"];

const reviews = [];
const seenPairs = {};
let reviewSeq = 400000;
const reviewCandidates = orderItemsIndex.filter(function (it) {
  return it.status === "DELIVERED" || it.status === "COMPLETED";
});
let hiddenCount = 0;
for (let i = 0; i < reviewCandidates.length; i++) {
  const it = reviewCandidates[i];
  if (rnd() > 0.85) { continue; }              // ~85% nguoi mua de lai danh gia
  const key = String(it.bookId) + "::" + String(it.userId);
  if (seenPairs[key]) { continue; }            // tranh vi pham unique index
  seenPairs[key] = true;

  const book = bookById[String(it.bookId)] || {};
  const bm = buyerMeta[String(it.userId)] || { username: "buyer", fullName: "Khach hang" };
  const seller = sellersById[String(it.sellerId)] || {};

  const rating = rnd() < 0.55 ? 5 :
    (rnd() < 0.65 ? 4 : (rnd() < 0.75 ? 3 : (rnd() < 0.6 ? 2 : 1)));
  const status = pick(REVIEW_STATUS);
  if (status === "HIDDEN") { hiddenCount++; }
  const createdAt = new Date(it.createdAt.getTime() + rint(2, 25) * DAY);

  const replies = [];
  const replyCount = rnd() < 0.35 ? rint(1, 2) : 0;
  for (let r = 0; r < replyCount; r++) {
    replies.push({
      replyId: L(900000 + reviews.length * 10 + r),
      userId: it.sellerId,
      user: { id: it.sellerId, username: seller.username || "shop", role: "SELLER" },
      content: "Cam on ban da danh gia, shop se tiep tuc phat huy chat luong!",
      createdAt: new Date(createdAt.getTime() + DAY)
    });
  }

  reviewSeq++;
  reviews.push({
    _id: L(reviewSeq),
    bookId: it.bookId,
    book: {
      title: book.title || "", imageUrl: (book.images || {}).thumbnail || null,
      sellerId: it.sellerId, categoryId: book.categoryId || null,
      categoryName: book.categoryName || null
    },
    userId: it.userId,
    user: {
      id: it.userId, username: bm.username, fullName: bm.fullName,
      avatarUrl: "/images/avatars/u" + it.userId + ".png"
    },
    orderId: it.orderId,
    isVerifiedPurchase: true,
    rating: I(rating),
    comment: pick(COMMENT_POOL[rating]),
    images: rnd() < 0.25
      ? [{ fileId: null, url: "/uploads/reviews/r" + it.bookId + "_1.jpg" },
        { fileId: null, url: "/uploads/reviews/r" + it.bookId + "_2.jpg" }]
      : [],
    replies: replies,
    helpfulCount: L(rint(0, 150)),
    moderation: {
      status: status,
      reason: status === "HIDDEN" ? "Noi dung khong phu hop" : null,
      byUserId: status === "HIDDEN" && adminUser ? adminUser._id : null,
      at: status === "HIDDEN" ? new Date(createdAt.getTime() + DAY) : null,
      history: [{ status: status, byUserId: null, at: createdAt }]
    },
    flags: {
      count: rnd() < 0.15 ? rint(1, 3) : 0,
      reasons: rnd() < 0.15 ? ["SPAM"] : []
    },
    createdAt: createdAt,
    updatedAt: createdAt,
    schemaVersion: I(1)
  });
}
insertBatched("reviews", reviews, 200);
const avgRating = reviews.reduce(function (s, r) { return s + r.rating; }, 0) / (reviews.length || 1);
print("    reviews = " + reviews.length + " (tu " + reviewCandidates.length +
  " dong hang da giao) | diem TB = " + Math.round(avgRating * 100) / 100 +
  " | HIDDEN = " + hiddenCount);
print("    so sach duoc danh gia = " + Object.keys(
  reviews.reduce(function (m, r) { m[String(r.bookId)] = 1; return m; }, {})).length);

// ===========================================================================
// [13] NOTIFICATIONS + NOTIFICATION_DELIVERIES
//      - Thong bao sinh THAT tu vong doi don hang (ORDER_CREATED -> DELIVERED)
//      - Moi thong bao co 1-2 lan gui (SSE bat buoc, EMAIL cho HIGH/URGENT)
//        de queue worker co du lieu retry: {status, nextRetryAt}
//      - expiresAt = createdAt + 90 ngay (TTL index lo don dep)
// ===========================================================================
title("[13] notifications / notification_deliveries");
const notifications = [];
const deliveries = [];
let notiSeq = 600000;
let deliverySeq = 650000;

function pushNoti(o, type, title, message, priority, at) {
  // notifications co TTL idx_notifications_expires (expiresAt): thong bao "sinh ra
  // da het han" se bi TTL xoa ngay -> khong tao nua (tranh dem lech so lieu).
  const expiresAt = new Date(at.getTime() + 90 * DAY);
  if (expiresAt.getTime() <= NOW.getTime()) { return; }
  const isRead = rnd() < 0.62 || type === "SYSTEM";
  notiSeq++;
  notifications.push({
    _id: L(notiSeq),
    userId: o.buyerId,
    type: type,
    title: title,
    message: message,
    payload: {
      orderId: o._id, orderCode: o.orderCode, type: type,
      deepLink: "/orders/" + o._id
    },
    isRead: isRead,
    priority: priority,
    readAt: isRead ? new Date(at.getTime() + 3600000) : null,
    expiresAt: expiresAt,                          // TTL idx_notifications_expires
    createdAt: at
  });
  const channels = (priority === "HIGH" || priority === "URGENT" || rnd() < 0.3)
    ? ["SSE", "EMAIL"] : ["SSE"];
  channels.forEach(function (ch) {
    const failed = rnd() < 0.12;
    const status = failed ? (rnd() < 0.5 ? "PENDING" : "FAILED") : "SENT";
    deliverySeq++;
    deliveries.push({
      _id: L(deliverySeq),
      notificationId: notiSeq,
      userId: o.buyerId,
      channel: ch,
      status: status,
      attemptCount: failed ? rint(1, 6) : 1,
      nextRetryAt: status === "PENDING" ? daysAgo(-1 * rint(0, 2)) : null,
      lastError: failed ? "Connection timeout" : null,
      sentAt: status === "SENT" ? new Date(at.getTime() + 2000) : null,
      createdAt: at,
      updatedAt: at
    });
  });
}

orders.forEach(function (o) {
  const t0 = o.createdAt;
  pushNoti(o, "ORDER_CREATED", "Dat hang thanh cong",
    "Don hang " + o.orderCode + " da duoc tao, dang cho nguoi ban xac nhan.",
    "NORMAL", t0);
  if (o.payment.status === "SUCCESS") {
    pushNoti(o, "PAYMENT_SUCCESS", "Thanh toan thanh cong",
      "Da nhan thanh toan " + money(o.totalAmount) + "d cho don " + o.orderCode + ".",
      "HIGH", new Date(t0.getTime() + 5 * 60000));
  } else if (o.payment.status === "CANCELLED") {
    pushNoti(o, "PAYMENT_FAILED", "Thanh toan that bai",
      "Giao dich cho don " + o.orderCode + " da bi huy, vui long thu lai.",
      "URGENT", new Date(t0.getTime() + 20 * 60000));
  }
  const chain = (o.subOrders && o.subOrders.length > 0 && o.subOrders[0].statusHistory) || [];
  const last = chain.length ? chain[chain.length - 1] : null;
  const t1 = (last && last.at) ? last.at : new Date(t0.getTime() + 2 * DAY);
  if (o.status === "SHIPPING" || o.status === "DELIVERED" || o.status === "COMPLETED") {
    pushNoti(o, "ORDER_SHIPPING", "Don hang dang duoc giao",
      "Don " + o.orderCode + " da ban giao cho don vi van chuyen.",
      "NORMAL", t1);
  }
  if (o.status === "COMPLETED") {
    pushNoti(o, "ORDER_DELIVERED", "Giao hang thanh cong",
      "Don " + o.orderCode + " da giao thanh cong. Hay danh gia de nhan diem!",
      "NORMAL", new Date(Math.min(t1.getTime() + 2 * DAY, Date.now())));
  }
  if (o.status === "CANCELLED" || o.status === "RETURNED") {
    pushNoti(o, "ORDER_STATUS_CHANGED", "Don hang da " +
      (o.status === "CANCELLED" ? "huy" : "tra hang"),
      "Don " + o.orderCode + " chuyen sang trang thai " + o.status + ".",
      "HIGH", t1);
  }
});

// Thong bao khuyen mai / he thong cho tung buyer
buyers.forEach(function (b, idx) {
  const at = daysAgo(rint(1, 60));
  pushNoti({ buyerId: b._id, _id: b._id, orderCode: "PROMO-" + idx },
    idx % 7 === 0 ? "SYSTEM" : "PROMOTION",
    idx % 7 === 0 ? "Bao tri he thong dinh ky" : "Ma giam gia danh rieng cho ban",
    idx % 7 === 0
      ? "Bookom bao tri dinh ky 02:00 - 03:00 hang tuan."
      : "Ban co voucher giam 15% cho don tiep theo, han dung trong 7 ngay.",
    idx % 7 === 0 ? "LOW" : pick(["NORMAL", "HIGH"]), at);
});

insertBatched("notifications", notifications, 200);
insertBatched("notification_deliveries", deliveries, 200);
print("    notifications = " + notifications.length +
  " (chua doc = " + notifications.filter(function (n) { return !n.isRead; }).length + ")");
print("    notification_deliveries = " + deliveries.length +
  " (PENDING retry = " + deliveries.filter(function (d) { return d.status === "PENDING"; }).length +
  ", FAILED = " + deliveries.filter(function (d) { return d.status === "FAILED"; }).length + ")");

// ===========================================================================
// [14] ACTIVITY_LOG - TIME-SERIES (timeField="ts", metaField="userId")
//      - Sinh THAT tu vong doi mua sam: SEARCH/VIEW_BOOK -> ADD_TO_CART ->
//        CHECKOUT -> PAYMENT -> THANH TOAN -> WRITE_REVIEW
//      - MongoDB gom bucket theo userId + TTL 180 ngay (xem 01_create_...)
//        => truy van "hanh vi cua 1 khach" / tan suat truy cap cuc nhanh
//      - metadata{device,durationMs,orderId,keyword} phuc vu phan tich them
// ===========================================================================
title("[14] activity_log (time-series)");
const DEVICES = ["web/chrome", "web/edge", "web/firefox", "mobile/android", "mobile/ios"];
const logs = [];

function logPush(userId, action, ts, bookId, extra) {
  logs.push({
    ts: ts,
    userId: userId,                                  // metaField: Long (nhu users._id)
    sessionId: "s" + rint(1, 900),                   // phien dang nhap gia lap
    action: action,
    bookId: bookId || null,
    metadata: Object.assign({ device: pick(DEVICES), durationMs: I(rint(150, 9000)) }, extra || {}),
    ip: "203.113." + rint(0, 255) + "." + rint(1, 254)
  });
}

// (a) Hanh vi gan voi tung dong hang da mua (900 dong dau -> ~6.000 su kien)
orderItemsIndex.slice(0, 900).forEach(function (it) {
  const book = bookById[String(it.bookId)] || {};
  const base = it.createdAt;
  const nView = rint(2, 4);
  for (let k = 0; k < nView; k++) {
    logPush(it.userId, k === 0 ? "SEARCH" : "VIEW_BOOK",
      new Date(base.getTime() - rint(30, 4320) * 60000), it.bookId,
      {
        sellerId: it.sellerId, categoryId: book.categoryId || null,
        keyword: k === 0 ? (book.categoryName || "sach") : null
      });
  }
  logPush(it.userId, "ADD_TO_CART", new Date(base.getTime() - rint(20, 90) * 60000), it.bookId,
    { orderId: it.orderId, quantity: it.quantity });
  logPush(it.userId, "CHECKOUT", base, null,
    { orderId: it.orderId, amount: I(money(it.unitPrice * it.quantity)) });
  if (it.status === "DELIVERED" || it.status === "COMPLETED" || it.status === "RETURNED") {
    logPush(it.userId, "PAYMENT", new Date(base.getTime() + 60000), null,
      { orderId: it.orderId, method: "VNPAY", status: "SUCCESS" });
    logPush(it.userId, "VIEW_ORDER", new Date(base.getTime() + rint(1, 5) * DAY), null,
      { orderId: it.orderId, status: it.status });
  } else if (it.status === "CANCELLED") {
    logPush(it.userId, "CANCEL_ORDER", new Date(base.getTime() + rint(1, 3) * DAY), null,
      { orderId: it.orderId, reason: "CHANGE_MIND" });
  }
});

// (b) Phien dang nhap + xem danh muc + wishlist (du lieu "browsing frequency")
buyers.forEach(function (u) {
  for (let m = 0; m < 6; m++) {
    const t = daysAgo(rint(0, 180));
    logPush(u._id, "LOGIN", t, null, { method: rnd() < 0.7 ? "PASSWORD" : "GOOGLE" });
    logPush(u._id, "LOGOUT", new Date(t.getTime() + rint(5, 120) * 60000), null, null);
    logPush(u._id, "VIEW_CATEGORY", daysAgo(rint(0, 180)),
      null, { categoryId: realCats.length ? pick(realCats)._id : null });
  }
});
// WISHLIST_ADD: doc truc tiep tu DB (buyers[] trong bo nho duoc doc TRUOC khi [4] cap nhat)
db.users.find({ role: "BUYER" }, { wishlist: 1 }).forEach(function (u) {
  const wish = ((u.wishlist || {}).bookIds) || [];
  wish.forEach(function (bid) {
    logPush(u._id, "WISHLIST_ADD", daysAgo(rint(0, 120)), bid, { source: "detail-page" });
  });
});

insertBatched("activity_log", logs, 500);
print("    activity_log = " + logs.length + " su kien (time-series, TTL 180 ngay)");
print("    top action   = " + JSON.stringify(db.activity_log.aggregate([
  { $group: { _id: "$action", n: { $sum: 1 } } },
  { $sort: { n: -1 } }, { $limit: 3 }
]).toArray()));

// ===========================================================================
// [15] DAILY_STATS - MATERIALIZED VIEW bang $merge (khong GROUP BY moi lan tai)
//      - Gom theo (ngay, nha ban): doanh thu / so don / so luong ban
//      - newBuyers: dem khach co DON DAU TIEN trong ngay (dung $lookup + $expr
//        de kiem tra "truoc do da mua chua") -> do luong "khach moi"
//      - $merge on:[dateKey, sellerId] khop unique index uq_daily_stats
// ===========================================================================
title("[15] daily_stats ($merge tu orders)");
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $ne: "CANCELLED" } } },
  {
    $lookup: {
      from: "orders",
      let: { b: "$buyerId", t: "$createdAt" },
      pipeline: [
        {
          $match: {
            $expr: {
              $and: [
                { $eq: ["$buyerId", "$$b"] },
                { $lt: ["$createdAt", "$$t"] },
                { $ne: ["$status", "CANCELLED"] }
              ]
            }
          }
        },
        { $count: "n" }
      ],
      as: "priorOrders"
    }
  },
  { $set: { isFirstOrder: { $eq: [{ $size: "$priorOrders" }, 0] } } },
  {
    $group: {
      _id: {
        dateKey: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } },
        sellerId: "$subOrders.sellerId"
      },
      revenue: { $sum: "$subOrders.subTotal" },
      orderCount: { $sum: 1 },
      unitsSold: { $sum: { $sum: "$subOrders.items.quantity" } },
      buyerSet: { $addToSet: "$buyerId" },
      firstBuyerSet: { $addToSet: { $cond: ["$isFirstOrder", "$buyerId", null] } }
    }
  },
  {
    $project: {
      _id: 0,
      dateKey: "$_id.dateKey",
      sellerId: "$_id.sellerId",
      revenue: 1, orderCount: 1, unitsSold: 1,
      buyerCount: { $size: "$buyerSet" },
      newBuyers: { $size: { $setDifference: ["$firstBuyerSet", [null]] } },
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
print("    daily_stats = " + db.daily_stats.countDocuments({}) + " dong (ngay x nha ban)");
print("    ky gan nhat = " + JSON.stringify(db.daily_stats.aggregate([
  { $group: { _id: null, ngay: { $max: "$dateKey" }, newBuyers: { $sum: "$newBuyers" } } },
  { $project: { _id: 0 } }
]).toArray()));

// ===========================================================================
// [16] ASSOCIATION_RULES - khai thac luat ket hop "mua kem" bang aggregation
//      (thay FP-Growth viet tay): gop order -> basket -> $facet dem cap sach
//      -> tinh support/confidence/lift. Ghi CA 2 CHIEU de tra cuu nhanh.
//      Rang buoc: uq_rules_pair {bookAId, bookBId} (unique) - moi cap 1 lan.
// ===========================================================================
title("[16] association_rules (khai thac mua kem)");
const mining = db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $in: ["COMPLETED", "DELIVERED"] } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $in: ["COMPLETED", "DELIVERED"] } } },
  { $unwind: "$subOrders.items" },
  { $group: { _id: "$_id", books: { $addToSet: "$subOrders.items.bookId" } } },
  { $match: { "books.1": { $exists: true } } },        // gio co >= 2 dau sach
  {
    $facet: {
      total: [{ $count: "n" }],
      bookCounts: [
        { $unwind: "$books" },
        { $group: { _id: "$books", n: { $sum: 1 } } }
      ],
      pairCounts: [
        // Phai NHAN BAN mang truoc khi $unwind 2 lan (neu khong se ra 0 cap)
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
mining.bookCounts.forEach(function (r) { bookCountMap[String(r._id)] = r.n; });

const rules = [];
if (totalBaskets > 0) {
  // Nguong PHU HOP DU LIEU THAT: gio hang that chi co ~330 gio (>= 2 dau sach)
  // => support tuyet doi rat nho, phai ha nguong so voi du lieu bia cua 03.
  const MIN_SUPPORT = 0.004;      // >= ~2 gio hang
  const MIN_CONFIDENCE = 0.08;    // P(B|A) >= 8%
  const MIN_LIFT = 1.0;           // chi giu luat co tuong quan duong
  const MAX_PAIRS = 1500;         // gioi han so cap (giu collection gon, tra cuu nhanh)
  const r2 = function (v) { return Math.round(v * 10000) / 10000; };
  const candidates = [];
  mining.pairCounts.forEach(function (p) {
    const a = p._id.a, b = p._id.b, pairCount = p.n;
    const countA = bookCountMap[String(a)] || 0, countB = bookCountMap[String(b)] || 0;
    if (countA === 0 || countB === 0) { return; }
    const support = pairCount / totalBaskets;
    const confAB = pairCount / countA;                           // P(B | A)
    const confBA = pairCount / countB;                           // P(A | B)
    const liftAB = confAB / (countB / totalBaskets);
    const liftBA = confBA / (countA / totalBaskets);
    if (support < MIN_SUPPORT || liftAB <= MIN_LIFT) { return; }
    if (confAB < MIN_CONFIDENCE && confBA < MIN_CONFIDENCE) { return; }
    candidates.push({ a: a, b: b, pairCount: pairCount, support: support,
      confAB: confAB, liftAB: liftAB, confBA: confBA, liftBA: liftBA });
  });
  // Uu tien cap "chat" nhat (tong confidence 2 chieu) roi moi cat bot
  candidates.sort(function (x, y) { return (y.confAB + y.confBA) - (x.confAB + x.confBA); });
  candidates.slice(0, MAX_PAIRS).forEach(function (c) {
    // Ghi CA 2 CHIEU (A->B va B->A) de tra cuu goi y "mua kem" nhanh
    rules.push({
      bookAId: c.a, bookBId: c.b, support: r2(c.support), confidence: r2(c.confAB),
      lift: r2(c.liftAB), transactionCount: I(c.pairCount), windowDays: I(365), updatedAt: NOW
    });
    rules.push({
      bookAId: c.b, bookBId: c.a, support: r2(c.support), confidence: r2(c.confBA),
      lift: r2(c.liftBA), transactionCount: I(c.pairCount), windowDays: I(365), updatedAt: NOW
    });
  });
}
if (rules.length > 0) { db.association_rules.insertMany(rules, { ordered: false }); }
print("    baskets=" + totalBaskets + " | cap sach=" + mining.pairCounts.length +
  " | luat ket hop=" + rules.length + " (2 chieu) | sach co goi y=" +
  db.association_rules.distinct("bookAId").length);

// ===========================================================================
// [17] TINH LAI CAC MATERIALIZED VIEW ($merge) TU DU LIEU VUA SINH
//      (03_seed_reference.js tinh tu du lieu bia; o day tinh lai tren du lieu THAT)
// ===========================================================================
title("[17] Materialized views ($merge)");

// (1) books.stats.soldCount / revenue  <- tu orders (chi subOrder khong huy)
const orderItemsUnwind = [
  { $match: { isDeleted: { $ne: true } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $nin: ["CANCELLED"] } } },
  { $unwind: "$subOrders.items" }
];
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
      whenMatched: [{ $set: { "stats.soldCount": "$$new.sold", "stats.revenue": "$$new.revenue" } }],
      whenNotMatched: "discard"
    }
  }
])).toArray();
print("    (1) books.stats.soldCount/revenue OK");

// (2) books.stats.viewCount <- tu activity_log (time-series, action=VIEW_BOOK)
db.activity_log.aggregate([
  { $match: { action: "VIEW_BOOK", bookId: { $ne: null } } },
  { $group: { _id: "$bookId", n: { $sum: 1 } } },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{ $set: { "stats.viewCount": "$$new.n" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (2) books.stats.viewCount OK (tu activity_log)");

// (3) books.stats.wishlistCount <- tu users.wishlist.bookIds (mang con nhung)
db.users.aggregate([
  { $match: { "wishlist.bookIds.0": { $exists: true } } },
  { $unwind: "$wishlist.bookIds" },
  { $group: { _id: "$wishlist.bookIds", n: { $sum: 1 } } },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{ $set: { "stats.wishlistCount": "$$new.n" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (3) books.stats.wishlistCount OK (tu users.wishlist)");

// (4) books.rating <- tu reviews (chi moderation.status = VISIBLE)
db.reviews.aggregate([
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
      count: 1, lastReviewAt: 1,
      distribution: {
        $arrayToObject: {
          $map: {
            input: [1, 2, 3, 4, 5],
            as: "star",
            in: {
              k: { $toString: "$$star" },
              v: { $size: { $filter: { input: "$stars", as: "r", cond: { $eq: ["$$r", "$$star"] } } } }
            }
          }
        }
      }
    }
  },
  {
    $merge: {
      into: "books", on: "_id",
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
print("    (4) books.rating OK (avg/count/distribution/lastReviewAt)");

// (5) books.topReviews - SUBSET PATTERN: 3 review moi nhat (WINDOW: $sort + $group)
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
  { $project: { topReviews: { $slice: ["$topReviews", 3] } } },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{ $set: { topReviews: "$$new.topReviews" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (5) books.topReviews OK (subset 3 phan tu)");

// (6) books.tags - nhan tu du lieu THAT (ban chay / danh gia cao / giam gia /
//     freeship / moi nhap / pho bien / sach hot) -> $setUnion cac mang $cond, $merge
//     Nguong duoc hieu chinh theo catalog THAT da import: gia 50k-250k, hien
//     chua co discountAmount>0, 962 sach da ban, 626 sach co danh gia.
const TAG_90D = new Date(NOW.getTime() - 90 * DAY);
db.books.aggregate([
  { $match: { approvalStatus: "APPROVED", isActive: true } },
  {
    $project: {
      tags: {
        $setUnion: [
          { $cond: [{ $gte: [{ $ifNull: ["$stats.soldCount", 0] }, 5] }, ["ban-chay"], []] },
          {
            $cond: [{
              $and: [
                { $gte: [{ $ifNull: ["$rating.count", 0] }, 2] },
                { $gte: [{ $ifNull: ["$rating.avg", 0] }, 4.0] }
              ]
            }, ["danh-gia-cao"], []]
          },
          { $cond: [{ $gt: [{ $ifNull: ["$discountAmount", 0] }, 0] }, ["giam-gia"], []] },
          { $cond: [{ $gte: [{ $ifNull: ["$finalPrice", 0] }, 180000] }, ["freeship"], []] },
          { $cond: [{ $gte: ["$createdAt", TAG_90D] }, ["moi-nhap"], []] },
          { $cond: [{ $gte: [{ $ifNull: ["$stats.viewCount", 0] }, 3] }, ["pho-bien"], []] },
          { $cond: [{ $gte: [{ $ifNull: ["$stats.wishlistCount", 0] }, 1] }, ["sach-hot"], []] }
        ]
      }
    }
  },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{ $set: { tags: "$$new.tags" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (6) books.tags OK -> " + JSON.stringify(db.books.aggregate([
  { $unwind: "$tags" }, { $group: { _id: "$tags", n: { $sum: 1 } } }, { $sort: { n: -1 } }
]).toArray()));

// (7) books.boughtTogether - SUBSET PATTERN: top 10 goi y tu association_rules
//     Chay lai nhieu lan van dung: xoa goi y CU truoc khi nap goi y MOI
db.books.updateMany({ "boughtTogether.0": { $exists: true } }, { $unset: { boughtTogether: "" } });
db.association_rules.aggregate([
  { $sort: { confidence: -1, lift: -1 } },
  {
    $group: {
      _id: "$bookAId",
      items: {
        $push: { bookId: "$bookBId", confidence: "$confidence", lift: "$lift", support: "$support" }
      }
    }
  },
  { $project: { items: { $slice: ["$items", 10] } } },
  {
    $merge: {
      into: "books", on: "_id",
      whenMatched: [{ $set: { boughtTogether: "$$new.items" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (7) books.boughtTogether OK -> " + db.books.countDocuments({ "boughtTogether.0": { $exists: true } }) + " sach");

// (8) users.stats <- tu orders + reviews + wishlist
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
  { $lookup: { from: "reviews", localField: "_id", foreignField: "userId", as: "revs" } },
  {
    $set: {
      avgOrderValue: { $round: [{ $divide: ["$totalSpent", "$orderCount"] }, 0] },
      reviewCount: { $size: "$revs" }
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
          "stats.reviewCount": "$$new.reviewCount",
          "stats.lastOrderAt": "$$new.lastOrderAt"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
db.users.updateMany({ role: "BUYER" }, [
  { $set: { "stats.wishlistCount": { $size: { $ifNull: ["$wishlist.bookIds", []] } } } }
]);
print("    (8) users.stats OK (orderCount/totalSpent/avgOrderValue/reviewCount/wishlistCount)");

// (9) users.ml - HO SO ML: 12 raw feature + RFM + 3 ket qua du doan
//     (9a) RFM + dac trung tu orders
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  {
    $group: {
      _id: "$buyerId",
      rfmFrequency: { $sum: 1 },
      rfmMonetary: { $sum: "$totalAmount" },
      rfmLastOrder: { $max: "$createdAt" },
      withCoupon: { $sum: { $cond: [{ $ne: [{ $ifNull: ["$couponCode", null] }, null] }, 1, 0] } },
      discountRatio: {
        $avg: { $cond: [{ $gt: ["$itemsTotal", 0] }, { $divide: ["$discountAmount", "$itemsTotal"] }, 0] }
      }
    }
  },
  {
    $set: {
      totalOrders: "$rfmFrequency",
      avgOrderValue: { $round: [{ $divide: ["$rfmMonetary", "$rfmFrequency"] }, 0] },
      discountUsageRate: { $round: [{ $divide: ["$withCoupon", "$rfmFrequency"] }, 4] },
      priceSensitivityIndex: { $round: ["$discountRatio", 4] },
      recencyDays: { $divide: [{ $subtract: ["$$NOW", "$rfmLastOrder"] }, 86400000] }
    }
  },
  {
    $set: {
      rfmSegment: {
        $switch: {
          branches: [
            { case: { $and: [{ $lte: ["$recencyDays", 30] }, { $gte: ["$rfmMonetary", 1000000] }] }, then: "VIP" },
            { case: { $and: [{ $lte: ["$recencyDays", 90] }, { $gte: ["$rfmFrequency", 2] }] }, then: "TRUNG_THANH" },
            { case: { $gte: ["$recencyDays", 180] }, then: "NGUY_CO_ROI" },
            { case: { $gte: ["$recencyDays", 90] }, then: "CAN_CHAM_SOC" }
          ],
          default: "MOI"
        }
      }
    }
  },
  { $lookup: { from: "users", localField: "_id", foreignField: "_id", as: "u" } },
  {
    $set: {
      accountAgeMonths: {
        $round: [{ $divide: [{ $subtract: ["$$NOW", { $ifNull: [{ $first: "$u.createdAt" }, "$$NOW"] }] }, 2592000000] }, 1]
      }
    }
  },
  {
    $merge: {
      into: "users", on: "_id",
      whenMatched: [{
        $set: {
          "ml.rfmFrequency": "$$new.rfmFrequency",
          "ml.rfmMonetary": "$$new.rfmMonetary",
          "ml.rfmLastOrder": "$$new.rfmLastOrder",
          "ml.rfmSegment": "$$new.rfmSegment",
          "ml.totalOrders": "$$new.totalOrders",
          "ml.avgOrderValue": "$$new.avgOrderValue",
          "ml.discountUsageRate": "$$new.discountUsageRate",
          "ml.priceSensitivityIndex": "$$new.priceSensitivityIndex",
          "ml.accountAgeMonths": "$$new.accountAgeMonths"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (9a) users.ml RFM OK -> " + JSON.stringify(db.users.aggregate([
  { $match: { "ml.rfmSegment": { $exists: true } } },
  { $group: { _id: "$ml.rfmSegment", soKhach: { $sum: 1 } } }, { $sort: { soKhach: -1 } }
]).toArray()));

//     (9b) 12 raw feature con lai (tu reviews / tickets / returns / carts) +
//          3 ket qua du doan (churnProbability -> predictedLabel/riskLevel)
db.users.aggregate([
  { $match: { role: "BUYER" } },
  { $lookup: { from: "reviews", localField: "_id", foreignField: "userId", as: "revs" } },
  { $lookup: { from: "support_tickets", localField: "_id", foreignField: "userId", as: "tickets" } },
  { $lookup: { from: "order_returns", localField: "_id", foreignField: "userId", as: "rets" } },
  { $lookup: { from: "carts", localField: "_id", foreignField: "buyerId", as: "cart" } },
  {
    $set: {
      recencyDays: {
        $divide: [{ $subtract: ["$$NOW", { $ifNull: ["$stats.lastOrderAt", "$$NOW"] }] }, 86400000]
      },
      customerSupportTickets: { $size: "$tickets" },
      returnRate: {
        $round: [{
          $divide: [{ $size: "$rets" },
            { $max: [{ $ifNull: ["$stats.orderCount", 0] }, 1] }]
        }, 4]
      },
      productReviewScoreAvg: { $round: [{ $ifNull: [{ $avg: "$revs.rating" }, 0] }, 2] },
      cartAbandonmentRate: {
        $cond: [
          { $eq: [{ $size: { $ifNull: ["$cart.items", []] } }, 0] }, 0,
          {
            $let: {
              vars: { idle: { $divide: [{ $subtract: ["$$NOW", { $max: "$cart.updatedAt" }] }, 86400000] } },
              in: {
                $switch: {
                  branches: [
                    { case: { $gt: ["$$idle", 7] }, then: 1 },
                    { case: { $gt: ["$$idle", 2] }, then: 0.5 }
                  ],
                  default: 0.15
                }
              }
            }
          }
        ]
      }
    }
  },
  {
    $set: {
      loyaltyMember: { $cond: [{ $gte: [{ $ifNull: ["$stats.orderCount", 0] }, 5] }, 1, 0] },
      satisfactionScore: { $min: [5, { $ifNull: [{ $avg: "$revs.rating" }, 3] }] },
      churnProbability: {
        $let: {
          vars: {
            z: {
              $add: [
                -1.3,
                { $multiply: [0.012, "$recencyDays"] },
                { $multiply: [-0.22, { $ifNull: ["$stats.orderCount", 0] }] },
                { $multiply: [0.4, "$customerSupportTickets"] },
                { $multiply: [0.7, "$returnRate"] },
                { $multiply: [0.6, "$cartAbandonmentRate"] }
              ]
            }
          },
          in: { $round: [{ $divide: [1, { $add: [1, { $exp: { $multiply: [-1, "$$z"] } }] }] }, 4] }
        }
      }
    }
  },
  {
    $set: {
      predictedLabel: { $cond: [{ $gte: ["$churnProbability", 0.5] }, 1, 0] },
      riskLevel: {
        $switch: {
          branches: [
            { case: { $gte: ["$churnProbability", 0.6] }, then: "HIGH" },
            { case: { $gte: ["$churnProbability", 0.3] }, then: "MEDIUM" }
          ],
          default: "LOW"
        }
      }
    }
  },
  {
    $merge: {
      into: "users", on: "_id",
      whenMatched: [{
        $set: {
          "ml.customerSupportTickets": "$$new.customerSupportTickets",
          "ml.loyaltyMember": "$$new.loyaltyMember",
          "ml.cartAbandonmentRate": "$$new.cartAbandonmentRate",
          "ml.productReviewScoreAvg": "$$new.productReviewScoreAvg",
          "ml.satisfactionScore": "$$new.satisfactionScore",
          "ml.returnRate": "$$new.returnRate",
          "ml.predictedLabel": "$$new.predictedLabel",
          "ml.churnProbability": "$$new.churnProbability",
          "ml.riskLevel": "$$new.riskLevel",
          "ml.lastAnalyzedAt": "$$NOW"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (9b) users.ml features + du doan OK -> " + JSON.stringify(db.users.aggregate([
  { $match: { "ml.riskLevel": { $exists: true } } },
  { $group: { _id: "$ml.riskLevel", soKhach: { $sum: 1 } } }, { $sort: { soKhach: -1 } }
]).toArray()));

//     (9c) browsingFrequencyPerWeek <- tu activity_log (30 ngay gan nhat)
db.activity_log.aggregate([
  { $match: { ts: { $gte: new Date(NOW.getTime() - 30 * DAY) } } },
  { $group: { _id: "$userId", n: { $sum: 1 } } },
  { $set: { perWeek: { $round: [{ $divide: ["$n", 4.3] }, 2] } } },
  {
    $merge: {
      into: "users", on: "_id",
      whenMatched: [{ $set: { "ml.browsingFrequencyPerWeek": "$$new.perWeek" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
db.users.updateMany({ role: "BUYER", "ml.browsingFrequencyPerWeek": { $exists: false } },
  { $set: { "ml.browsingFrequencyPerWeek": 0 } });
print("    (9c) users.ml.browsingFrequencyPerWeek OK (tu activity_log)");

// (10) seller_shops.stats <- tu orders / books
const SHOP_30D = new Date(NOW.getTime() - 30 * DAY);
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true } } },
  { $unwind: "$subOrders" },
  {
    $group: {
      _id: "$subOrders.sellerId",
      unitsSold: { $sum: { $sum: "$subOrders.items.quantity" } },
      revenue30d: {
        $sum: {
          $cond: [{
            $and: [{ $gte: ["$createdAt", SHOP_30D] }, { $ne: ["$subOrders.status", "CANCELLED"] }]
          }, "$subOrders.subTotal", 0]
        }
      },
      orderCount30d: {
        $sum: {
          $cond: [{
            $and: [{ $gte: ["$createdAt", SHOP_30D] }, { $ne: ["$subOrders.status", "CANCELLED"] }]
          }, 1, 0]
        }
      },
      shippedOrders: { $sum: { $cond: [{ $in: ["$subOrders.status", ["DELIVERED", "COMPLETED"]] }, 1, 0] } }
    }
  },
  { $project: { _id: 0, sellerId: "$_id", unitsSold: 1, revenue30d: 1, orderCount30d: 1, shippedOrders: 1 } },
  {
    $merge: {
      into: "seller_shops", on: "sellerId",
      whenMatched: [{
        $set: {
          "stats.unitsSold": "$$new.unitsSold",
          "stats.revenue30d": "$$new.revenue30d",
          "stats.orderCount30d": "$$new.orderCount30d",
          "stats.shippedOrders": "$$new.shippedOrders",
          updatedAt: "$$NOW"
        }
      }],
      whenNotMatched: "discard"
    }
  }
]).toArray();

// productCount: dem SACH THAT dang ban cua tung shop
db.books.aggregate([
  { $match: { approvalStatus: "APPROVED", isActive: true } },
  { $group: { _id: "$sellerId", n: { $sum: 1 } } },
  { $project: { _id: 0, sellerId: "$_id", n: 1 } },
  {
    $merge: {
      into: "seller_shops", on: "sellerId",
      whenMatched: [{ $set: { "stats.productCount": "$$new.n" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();

// responseRate: ti le phan hoi (on dinh theo sellerId - demo cho dashboard)
db.seller_shops.updateMany({}, [{
  $set: {
    "stats.responseRate": {
      $round: [{ $add: [0.7, { $divide: [{ $mod: ["$sellerId", 25] }, 100] }] }, 2]
    }
  }
}]);
print("    (10) seller_shops.stats OK -> " + db.seller_shops.countDocuments({ "stats.revenue30d": { $gt: 0 } }) +
  " shop co doanh thu 30 ngay");

// (11) categories.bookCount <- dem sach THAT theo danh muc
db.books.aggregate([
  { $match: { approvalStatus: "APPROVED", isActive: true } },
  { $group: { _id: "$categoryId", n: { $sum: 1 } } },
  {
    $merge: {
      into: "categories", on: "_id",
      whenMatched: [{ $set: { bookCount: "$$new.n" } }],
      whenNotMatched: "discard"
    }
  }
]).toArray();
print("    (11) categories.bookCount OK -> " + JSON.stringify(db.categories.find({}, { name: 1, bookCount: 1, _id: 0 })
  .sort({ bookCount: -1 }).limit(5).toArray()));

// ===========================================================================
// [18] DONG BO COUNTERS (cap Long id nhu SQL: counters + $inc atomic)
//      Chay lai nhieu lan VAN dung: dung $max -> chi nang chu khong giam.
// ===========================================================================
title("[18] Dong bo counters");
["users", "books", "carts", "orders", "sub_orders", "reviews",
  "notifications", "notification_deliveries", "payment_transactions",
  "order_returns", "support_tickets", "coupons", "categories",
  "seller_shops", "association_rules", "order_item_ids"].forEach(function (name) {
    const coll = (name === "sub_orders" || name === "order_item_ids") ? "orders" : name;
    const last = db.getCollection(coll).find({}, { _id: 1 }).sort({ _id: -1 }).limit(1).toArray();
    const maxId = (last.length && typeof last[0]._id === "number") ? last[0]._id : 0;
    const bump = (name === "sub_orders" || name === "order_item_ids") ? 900000 : 1000;
    db.counters.updateOne({ _id: name },
      { $max: { seq: maxId + bump }, $setOnInsert: { createdAt: new Date() } },
      { upsert: true });
  });
print("    counters = " + db.counters.countDocuments({}) + " ban ghi (da $max seq)");

// ===========================================================================
// [19] TONG KET
// ===========================================================================
print("\n==============================================================");
print("TONG KET DU LIEU GIAO DICH (13_demo_transactions.js)");
print("==============================================================");
[
  ["carts", "1 gio / buyer"],
  ["orders", "aggregate root + subOrders[].items[]"],
  ["reviews", "chi review tu don DA GIAO (verified purchase)"],
  ["payment_transactions", "moi don >= 1 giao dich (co ca FAILED)"],
  ["order_returns", "tra hang gan voi item da mua"],
  ["support_tickets", "ho tro khach hang"],
  ["notifications", "thong bao trong app"],
  ["notification_deliveries", "nhat ky gui email/push"],
  ["activity_log", "TIME-SERIES (TTL 180 ngay)"],
  ["daily_stats", "MATERIALIZED VIEW theo ngay x nha ban"],
  ["association_rules", "MATERIALIZED VIEW luat mua kem"],
  ["coupons", "voucher cua shop / he thong"]
].forEach(function (row) {
  print("  " + (row[0] + "                     ").substring(0, 22) +
    db.getCollection(row[0]).countDocuments({}) + "  <- " + row[1]);
});
print("--------------------------------------------------------------");
print("  books.stats.soldCount > 0 : " + db.books.countDocuments({ "stats.soldCount": { $gt: 0 } }));
print("  books.rating.count  > 0   : " + db.books.countDocuments({ "rating.count": { $gt: 0 } }));
print("  books.tags.0              : " + db.books.countDocuments({ "tags.0": { $exists: true } }));
print("  books.boughtTogether.0    : " + db.books.countDocuments({ "boughtTogether.0": { $exists: true } }));
print("  users.stats.orderCount>0  : " + db.users.countDocuments({ "stats.orderCount": { $gt: 0 } }));
print("  users.ml.rfmSegment       : " + db.users.countDocuments({ "ml.rfmSegment": { $exists: true } }));
print("  seller_shops.stats        : " + db.seller_shops.countDocuments({ "stats.productCount": { $gt: 0 } }));
const finalStats = db.stats(1024 * 1024);
print("--------------------------------------------------------------");
print("  dataSize: " + finalStats.dataSize + " MB | storageSize: " + finalStats.storageSize + " MB");
print("=== HOAN TAT 13_demo_transactions.js ===");
print("   Buoc tiep: db\\mongo\\06_verify_db.js  |  tools\\smoke-test-api.ps1");
