// ============================================================================
// 10_purge_fake_data.js - XOA TOAN BO DU LIEU BIA (seed gia) khoi DB
//
//   Xoa:
//     - users co passwordHash = "$2a$10$seedHash<id>"  (hash GIA, khong login duoc)
//     - books / seller_shops (du lieu bia)  -> se duoc tao lai bang du lieu that
//     - orders, reviews, carts, notifications, notification_deliveries,
//       activity_log, daily_stats, customer_ml, payment_transactions,
//       order_returns, support_tickets, user_security_events, refresh_tokens,
//       otp_codes, distributed_locks, association_rules, coupons
//   Giu:
//     - categories (20 danh muc that)
//     - 3 tai khoan that (hash $2a$12$): buyer cua ban, admin@gmail.com, shop_nha_nam@gmail.com
//     - validator + index cua moi collection (chi xoa DOCUMENT, khong drop collection)
//
// Chay:
//   tools\mongo-rebuild-data.bat            -> DRY RUN (chi in ra)
//   set APPLY=1 && tools\mongo-rebuild-data.bat   -> xoa that
// ============================================================================

const APPLY = process.env.APPLY === "1";
const FAKE_HASH = /^\$2a\$10\$seedHash/;

// Xoa sach document (giu collection/validator/index)
const PURGE_ALL = [
  "orders", "reviews", "carts", "notifications", "notification_deliveries",
  "activity_log", "daily_stats", "customer_ml", "payment_transactions",
  "order_returns", "support_tickets", "user_security_events", "refresh_tokens",
  "otp_codes", "distributed_locks", "association_rules", "coupons"
];
// Du lieu bia -> xoa de tao lai bang du lieu that
const PURGE_REBUILD = ["books", "seller_shops"];

function counts() {
  const out = {};
  db.getCollectionNames().filter(function (c) { return c.indexOf("system.") !== 0; })
    .sort().forEach(function (c) { out[c] = db.getCollection(c).countDocuments({}); });
  return out;
}

print("==============================================================");
print(" 10_purge_fake_data.js | che do = " + (APPLY ? "APPLY (XOA THAT)" : "DRY RUN (khong xoa)"));
print("==============================================================");

const before = counts();
print("");
print("TRUOC  (tong " + db.getCollectionNames().length + " collection):");
Object.keys(before).forEach(function (c) { print("   " + c + " = " + before[c]); });

const fakeUsers = db.users.countDocuments({ passwordHash: FAKE_HASH });
const realUsers = db.users.countDocuments({ passwordHash: { $not: FAKE_HASH } });
print("");
print("users: fake(hash seedHash)=" + fakeUsers + " | that(bcrypt 12)=" + realUsers);
db.users.find({ passwordHash: { $not: FAKE_HASH } }).forEach(function (u) {
  print("   GIU: _id=" + u._id + " | " + u.username + " | " + u.role);
});

if (!APPLY) {
  print("");
  print("DRY RUN - chua xoa gi. Chay lai voi APPLY=1 de xoa that.");
} else {
  print("");
  print("--- BAT DAU XOA ---");
  const r1 = db.users.deleteMany({ passwordHash: FAKE_HASH });
  print("   users(fake)                : da xoa " + r1.deletedCount);
  PURGE_ALL.forEach(function (c) {
    const r = db.getCollection(c).deleteMany({});
    print("   " + (c + "                          ").substring(0, 27) + ": da xoa " + r.deletedCount);
  });
  PURGE_REBUILD.forEach(function (c) {
    const r = db.getCollection(c).deleteMany({});
    print("   " + (c + "                          ").substring(0, 27) + ": da xoa " + r.deletedCount);
  });

  // counters: giu users.seq = max(_id) (khong de trung id), cac counter khac ve 0
  const maxUser = db.users.find({}).sort({ _id: -1 }).limit(1).toArray()[0];
  const userSeq = maxUser ? Number(maxUser._id) : 0;
  const now = new Date();
  db.counters.find().forEach(function (counter) {
    const seq = (counter._id === "users") ? userSeq : 0;
    const set = { seq: NumberLong(String(seq)), updatedAt: now };
    if (!counter.createdAt) { set.createdAt = now; }
    db.counters.updateOne({ _id: counter._id }, { $set: set });
  });
  print("   counters                   : users.seq=" + userSeq + " | cac counter khac = 0");
}

const after = counts();
print("");
print("SAU    (tong " + db.getCollectionNames().length + " collection):");
Object.keys(after).forEach(function (c) { print("   " + c + " = " + after[c]); });
print("");
print("Ghi chu: orders/reviews/carts/notifications/log se = 0 va TANG LAI khi dung that.");
print("Buoc tiep theo: tools\\mongo-import-sellers.bat  ->  tools\\mongo-import-books.bat");
