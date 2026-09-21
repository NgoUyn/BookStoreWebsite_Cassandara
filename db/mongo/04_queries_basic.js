// ============================================================================
// 04_queries_basic.js  - 15 TRUY VAN CO BAN (tieu chi 3: truy van tren GUI tool)
//   Chay: tools\mongo-run-scripts.bat
//   Hoac chay tung cau tren MongoDB Compass (tab Find / Aggregation).
//   Moi cau ghi ro INDEX duoc dung -> chup man hinh + giai thich vao bao cao.
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 04_queries_basic.js | DB = " + db.getName() + " ===");

function title(t) { print("\n===== " + t + " ====="); }

// ---------------------------------------------------------------------------
title("Q1. Trang chu: sach da duyet dang ban, sap theo gia, gioi han 5");
// Index: idx_books_catalog {approvalStatus,isActive,categoryId}
const q1 = db.books.find(
  { approvalStatus: "APPROVED", isActive: true },
  { title: 1, author: 1, finalPrice: 1, "rating.avg": 1, stockQuantity: 1 }
).sort({ finalPrice: 1 }).limit(5).toArray();
print("Ket qua: " + q1.length + " sach");
q1.forEach(b => print("  #" + b._id + " " + b.title.substring(0, 45) +
  " | " + b.finalPrice + "d | rating=" + b.rating.avg + " | ton=" + b.stockQuantity));

// ---------------------------------------------------------------------------
title("Q2. Loc gia trong khoang + nam xuat ban >= 2020 (range query)");
// Index: idx_books_price_rating {finalPrice,"rating.avg"} + idx_books_publish_year
print("So sach gia 100k-200k xuat ban tu 2020: " + db.books.countDocuments({
  finalPrice: { $gte: 100000, $lte: 200000 },
  publishYear: { $gte: 2020 },
  approvalStatus: "APPROVED"
}));
printjson(db.books.find({
  finalPrice: { $gte: 100000, $lte: 200000 }, publishYear: { $gte: 2020 },
  approvalStatus: "APPROVED"
}, { title: 1, finalPrice: 1, publishYear: 1, _id: 0 }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("Q3. Autocomplete: tieu de bat dau bang 'Nhung nguoi' (regex ANCHORED)");
// Regex phai co ^ moi tan dung duoc index (khac han LIKE '%...%' cua SQL)
const q3 = db.books.find(
  { title: { $regex: "^Nhung nguoi", $options: "i" }, approvalStatus: "APPROVED" },
  { title: 1, author: 1, _id: 0 }
).limit(5).toArray();
print("Tim thay: " + q3.length);
q3.forEach(b => print("  " + b.title + " / " + b.author));

// ---------------------------------------------------------------------------
title("Q4. Loc theo TAG (mang con - multikey index idx_books_tags)");
print("So sach co tag 'ban chay': " + db.books.countDocuments({ tags: "ban chay" }));
printjson(db.books.find({ tags: "ban chay", approvalStatus: "APPROVED" },
  { title: 1, tags: 1, _id: 0 }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("Q5. Don hang cua 1 khach, sap moi nhat, PHAN TRANG");
// Index: idx_orders_buyer_created {buyerId, createdAt:-1}
const buyerId = db.orders.findOne({}, { buyerId: 1 }).buyerId;
print("Buyer #" + buyerId + " co " + db.orders.countDocuments({ buyerId: buyerId }) + " don. Trang 1:");
db.orders.find({ buyerId: buyerId },
  { orderCode: 1, totalAmount: 1, status: 1, createdAt: 1, _id: 0 })
  .sort({ createdAt: -1 }).skip(0).limit(3).forEach(o =>
    print("  " + o.orderCode + " | " + o.totalAmount + "d | " + o.status +
      " | " + o.createdAt.toISOString().substring(0, 10)));

// ---------------------------------------------------------------------------
title("Q6. Thong bao CHUA DOC cua 1 user");
// Index: idx_notifications_user {userId,isRead,createdAt:-1}
const notiUser = db.notifications.findOne({ isRead: false }).userId;
print("User #" + notiUser + " co " +
  db.notifications.countDocuments({ userId: notiUser, isRead: false }) + " thong bao chua doc:");
printjson(db.notifications.find({ userId: notiUser, isRead: false },
  { title: 1, priority: 1, createdAt: 1 }).sort({ createdAt: -1 }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("Q7. Gio hang cua buyer + $elemMatch (tim item cu the)");
const cart = db.carts.findOne({ buyerId: db.carts.findOne({}).buyerId });
print("Cart #" + cart._id + " co " + cart.items.length + " item, tam tinh " + cart.totals.subtotal + "d");
printjson(cart.items.slice(0, 2));
const someBookId = cart.items[0].bookId;
print("$elemMatch: so gio hang dang chua sach #" + someBookId + " = " +
  db.carts.countDocuments({ items: { $elemMatch: { bookId: someBookId } } }));

// ---------------------------------------------------------------------------
title("Q8. User theo role + trang thai (loc, dem, distinct)");
// Index: idx_users_role_active {role,isActive}
print("SELLER hoat dong : " + db.users.countDocuments({ role: "SELLER", isActive: true }));
print("SELLER bi khoa   : " + db.users.countDocuments({ role: "SELLER", isActive: false }));
print("BUYER hoat dong  : " + db.users.countDocuments({ role: "BUYER", isActive: true }));
print("So tinh/thanh khac nhau: " + db.users.distinct("addresses.province").length);

// ---------------------------------------------------------------------------
title("Q9. Don dang xu ly cua 1 seller ($elemMatch tren mang long)");
// Index: idx_orders_seller_status {"subOrders.sellerId","subOrders.status",createdAt}
const sellerId = db.orders.findOne({}, { "subOrders.sellerId": 1 }).subOrders[0].sellerId;
print("Seller #" + sellerId + " co " + db.orders.countDocuments({
  subOrders: { $elemMatch: { sellerId: sellerId, status: "PROCESSING" } }
}) + " don dang xu ly (PROCESSING)");

// ---------------------------------------------------------------------------
title("Q10. Review 5 sao cua 1 sach (sap theo huu ich)");
// Index: idx_reviews_book_visible + idx_reviews_rating + idx_reviews_helpful
const ratedBook = db.reviews.findOne({ rating: 5 });
print("Sach #" + ratedBook.bookId + " - review 5 sao:");
printjson(db.reviews.find(
  { bookId: ratedBook.bookId, rating: 5, "moderation.status": "VISIBLE" },
  { "user.username": 1, rating: 1, comment: 1, helpfulCount: 1, _id: 0 })
  .sort({ helpfulCount: -1 }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("Q11. Tim voucher theo code KHONG phan biet hoa/thuong (COLLATION)");
// Index: uq_coupons_code_ci (collation en/strength 2) => khong can LOWER() nhu SQL
const anyCoupon = db.coupons.findOne({});
const lowerCode = anyCoupon.code.toLowerCase();
print("Ma goc: " + anyCoupon.code + " | tim bang chu thuong '" + lowerCode + "'");
print("  + co collation   : " + db.coupons.find({ code: lowerCode })
  .collation({ locale: "en", strength: 2 }).toArray().length + " ket qua");
print("  + khong collation: " + db.coupons.find({ code: lowerCode }).toArray().length + " ket qua");

// ---------------------------------------------------------------------------
title("Q12. Top sach ban chay (doc counter denormalized - khong GROUP BY)");
// Index: idx_books_sold_count {"stats.soldCount":-1}
db.books.find({ approvalStatus: "APPROVED" },
  { title: 1, "stats.soldCount": 1, "stats.revenue": 1 })
  .sort({ "stats.soldCount": -1 }).limit(5).forEach(b =>
    print("  #" + b._id + " " + b.title.substring(0, 40) + " | da ban " + b.stats.soldCount));

// ---------------------------------------------------------------------------
title("Q13. Canh bao ton kho (toan tu so sanh + $exists)");
print("Sap het (ton < 20)      : " + db.books.countDocuments({ stockQuantity: { $lt: 20 }, isActive: true }));
print("Het hang (ton = 0)      : " + db.books.countDocuments({ stockQuantity: 0 }));
print("Khong co field ton kho  : " + db.books.countDocuments({ stockQuantity: { $exists: false } }));

// ---------------------------------------------------------------------------
title("Q14. Danh sach nha xuat ban (distinct)");
const publishers = db.books.distinct("publisher");
print("So NXB: " + publishers.length);
printjson(publishers);

// ---------------------------------------------------------------------------
title("Q15. CAP NHAT du lieu: $inc ton kho, $push/$pull gio hang");
const targetBook = db.books.findOne({ stockQuantity: { $gt: 50 } }, { stockQuantity: 1 });
print("Truoc: sach #" + targetBook._id + " ton = " + targetBook.stockQuantity);
db.books.updateOne({ _id: targetBook._id }, { $inc: { stockQuantity: -2, "stats.soldCount": 2 } });
print("Sau $inc(-2) => ton = " + db.books.findOne({ _id: targetBook._id }).stockQuantity);

const cartId = db.carts.findOne({})._id;
const newBook = db.books.findOne({ approvalStatus: "APPROVED" });
db.carts.updateOne({ _id: cartId }, {
  $push: {
    items: {
      itemId: newBook._id, bookId: newBook._id, sellerId: newBook.sellerId,
      title: newBook.title, unitPrice: newBook.finalPrice, quantity: 1, addedAt: new Date()
    }
  }
});
print("Sau $push => gio #" + cartId + " co " + db.carts.findOne({ _id: cartId }).items.length + " item");
db.carts.updateOne({ _id: cartId }, { $pull: { items: { bookId: newBook._id } } });
print("Sau $pull => gio #" + cartId + " co " + db.carts.findOne({ _id: cartId }).items.length + " item");

db.books.updateOne({ _id: targetBook._id }, { $inc: { stockQuantity: 2, "stats.soldCount": -2 } });
print("Hoan tac $inc nguoc => ton = " + db.books.findOne({ _id: targetBook._id }).stockQuantity);

print("\n=== HOAN TAT 04_queries_basic.js ===");