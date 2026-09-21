// ============================================================================
// 02_indexes.js
// Tao TOAN BO index cho BOOKOM (index-as-code - 1 nguon su that duy nhat).
//
// Chay: tools\mongo-run-scripts.bat   (hoac mongosh --file)
// IDEMPOTENT: da co thi bo qua (bao IndexOptionsConflict neu option khac).
//
// QUY UOC TEN INDEX:
//   idx_<collection>_<field[_field...]>   (index thuong / compound / multikey)
//   uq_<collection>_<field...>            (unique)
//   ttl_<collection>_<field>              (TTL)
//   txt_<collection>_<field...>           (text search)
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 02_indexes.js | DB = " + db.getName() + " ===");

function ensureIndex(coll, keys, options) {
  const opts = Object.assign({}, options || {});
  if (!opts.name) {
    const flat = Object.keys(keys).join("_").replace(/[^A-Za-z0-9_]/g, "");
    opts.name = "idx_" + coll + "_" + flat;
  }
  try {
    const name = db.getCollection(coll).createIndex(keys, opts);
    print("  + " + coll + " -> " + name);
  } catch (e) {
    // Tu chua lanh: index da ton tai nhung KHAC option (vi du doi sparse -> partial)
    // -> xoa roi tao lai de script luon dat dung trang thai mong muon.
    if (/IndexOptionsConflict|IndexKeySpecsConflict|already exists|same name as the requested index/i.test(e.message)) {
      try {
        db.getCollection(coll).dropIndex(opts.name);
        const name = db.getCollection(coll).createIndex(keys, opts);
        print("  ~ " + coll + " -> " + name + " (da tao lai voi option moi)");
        return;
      } catch (e2) {
        print("  ! " + coll + " -> " + opts.name + " (" + e2.message + ")");
        return;
      }
    }
    print("  ! " + coll + " -> " + opts.name + " (" + e.message + ")");
  }
}

// ===========================================================================
// users  (aggregate: profile + addresses[] + wishlist + ml + stats)
// ===========================================================================
ensureIndex("users", { username: 1 }, { unique: true, name: "uq_users_username" });
// PARTIAL thay vi sparse: tranh loi trung null khi nhieu user chua co email
ensureIndex("users", { email: 1 }, {
  unique: true,
  partialFilterExpression: { email: { $type: "string" } },
  name: "uq_users_email"
});
ensureIndex("users", { phone: 1 }, {
  unique: true,
  partialFilterExpression: { phone: { $gt: "" } },
  name: "uq_users_phone"
});
ensureIndex("users", { role: 1, isActive: 1 }, { name: "idx_users_role_active" });
ensureIndex("users", { "stats.lastOrderAt": -1 }, { name: "idx_users_last_order" });
ensureIndex("users", { "ml.riskLevel": 1, "ml.churnProbability": -1 }, { name: "idx_users_ml_risk" });
ensureIndex("users", { "wishlist.bookIds": 1 }, { name: "idx_users_wishlist_books" });
ensureIndex("users",
  { username: "text", email: "text", "profile.firstName": "text", "profile.lastName": "text" },
  { default_language: "none", name: "txt_users" });

// ===========================================================================
// books  (aggregate: images + rating summary + stats + tags + boughtTogether)
// ===========================================================================
// 1. Trang chu / danh muc: loc theo trang thai + danh muc
ensureIndex("books", { author: 1, approvalStatus: 1, isActive: 1 }, { name: "idx_books_author_status" });
ensureIndex("books", { approvalStatus: 1, isActive: 1, categoryId: 1 }, { name: "idx_books_catalog" });
// 2. Sap xep gia + diem danh gia (range + sort cung luc)
ensureIndex("books", { finalPrice: 1, "rating.avg": -1 }, { name: "idx_books_price_rating" });
// 3. Trang seller: quan ly sach cua minh theo trang thai
ensureIndex("books", { sellerId: 1, approvalStatus: 1, createdAt: -1 }, { name: "idx_books_seller_status" });
// 4. Loc theo nam xuat ban (partial: chi index khi co gia tri)
ensureIndex("books", { publishYear: 1 }, {
  partialFilterExpression: { publishYear: { $exists: true } },
  name: "idx_books_publish_year"
});
// 5. Full-text search co trong so (title quan trong nhat)
ensureIndex("books",
  { title: "text", author: "text", publisher: "text", tags: "text" },
  { weights: { title: 10, author: 5, tags: 3, publisher: 1 }, default_language: "none", name: "txt_books" });
// 6. Multikey: loc theo tag
ensureIndex("books", { tags: 1 }, { name: "idx_books_tags" });
// 7. Partial index: chi danh index cho sach dang ghim (tiet kiem dung luong)
ensureIndex("books", { isPinned: -1, createdAt: -1 }, {
  partialFilterExpression: { isActive: true, isPinned: true },
  name: "idx_books_pinned"
});
// 8. Ban chay / xem nhieu
ensureIndex("books", { "stats.soldCount": -1 }, { name: "idx_books_sold_count" });
ensureIndex("books", { "stats.viewCount": -1 }, { name: "idx_books_view_count" });

// ===========================================================================
// carts  (aggregate: 1 document / buyer, items[] embedded)
// ===========================================================================
ensureIndex("carts", { buyerId: 1 }, { unique: true, name: "uq_carts_buyer" });
ensureIndex("carts", { "items.bookId": 1 }, { name: "idx_carts_items_book" });
ensureIndex("carts", { "items.sellerId": 1 }, { name: "idx_carts_items_seller" });

// ===========================================================================
// orders  (aggregate: subOrders[].items[] embedded)
// ===========================================================================
ensureIndex("orders", { orderCode: 1 }, { unique: true, name: "uq_orders_code" });
ensureIndex("orders", { buyerId: 1, createdAt: -1 }, { name: "idx_orders_buyer_created" });
// Trang seller: loc subOrder theo seller + trang thai (multikey)
ensureIndex("orders", { "subOrders.sellerId": 1, "subOrders.status": 1, createdAt: -1 },
  { name: "idx_orders_seller_status" });
ensureIndex("orders", { "subOrders.subOrderId": 1 }, { name: "idx_orders_suborder_id" });
ensureIndex("orders", { "subOrders.items.bookId": 1 }, { name: "idx_orders_items_book" });
ensureIndex("orders", { createdAt: -1 }, { name: "idx_orders_created" });
ensureIndex("orders", { status: 1, createdAt: -1 }, { name: "idx_orders_status" });
ensureIndex("orders", { couponCode: 1 }, { sparse: true, name: "idx_orders_coupon" });
ensureIndex("orders", { "payment.status": 1, createdAt: -1 }, {
  partialFilterExpression: { "payment.status": "PENDING" },
  name: "idx_orders_payment_pending"
});

// ===========================================================================
// seller_shops  (aggregate: seller snapshot + geo + stats)
// ===========================================================================
ensureIndex("seller_shops", { slug: 1 }, { unique: true, name: "uq_shops_slug" });
ensureIndex("seller_shops", { sellerId: 1 }, { unique: true, name: "uq_shops_seller" });
ensureIndex("seller_shops", { location: "2dsphere" }, { name: "geo_shops_location" });
ensureIndex("seller_shops", { approvalStatus: 1, createdAt: -1 }, { name: "idx_shops_status" });
ensureIndex("seller_shops", { stats: 1 }, { name: "idx_shops_stats" });
ensureIndex("seller_shops", { shopName: "text", description: "text" },
  { default_language: "none", name: "txt_shops" });

// ===========================================================================
// reviews  (aggregate RIENG: images[] + replies[] + moderation)
// ===========================================================================
// Rang buoc "1 user = 1 review / sach" o MUC DB (uu diem so voi phuong an embed)
ensureIndex("reviews", { bookId: 1, userId: 1 }, { unique: true, name: "uq_reviews_book_user" });
ensureIndex("reviews", { bookId: 1, "moderation.status": 1, createdAt: -1 },
  { name: "idx_reviews_book_visible" });
ensureIndex("reviews", { userId: 1, createdAt: -1 }, { name: "idx_reviews_user" });
ensureIndex("reviews", { bookId: 1, helpfulCount: -1 }, { name: "idx_reviews_helpful" });
ensureIndex("reviews", { rating: 1, createdAt: -1 }, { name: "idx_reviews_rating" });
ensureIndex("reviews", { comment: "text" }, { default_language: "none", name: "txt_reviews" });

// ===========================================================================
// categories
// ===========================================================================
ensureIndex("categories", { slug: 1 }, { unique: true, name: "uq_categories_slug" });
ensureIndex("categories", { name: 1 }, {
  unique: true,
  collation: { locale: "vi", strength: 2 },
  name: "uq_categories_name_ci"
});
ensureIndex("categories", { parentId: 1 }, { name: "idx_categories_parent" });
ensureIndex("categories", { path: 1 }, { name: "idx_categories_path" });

// ===========================================================================
// coupons  (khong can LOWER(code) nhu SQL nho COLLATION)
// ===========================================================================
ensureIndex("coupons", { code: 1 }, {
  unique: true,
  collation: { locale: "en", strength: 2 },
  name: "uq_coupons_code_ci"
});
// Partial index: chi index voucher dang bat (tiet kiem, dung cho job het han)
ensureIndex("coupons", { expiresAt: 1 }, {
  partialFilterExpression: { isActive: true },
  name: "idx_coupons_expires_active"
});
ensureIndex("coupons", { sellerId: 1, isActive: 1, createdAt: -1 }, { name: "idx_coupons_seller" });
ensureIndex("coupons", { scope: 1, isActive: 1 }, { name: "idx_coupons_scope" });

// ===========================================================================
// notifications  (+ TTL: tu dong xoa thong bao cu)
// ===========================================================================
ensureIndex("notifications", { userId: 1, isRead: 1, createdAt: -1 }, { name: "idx_notifications_user" });
ensureIndex("notifications", { createdAt: -1 }, { name: "idx_notifications_created" });
ensureIndex("notifications", { expiresAt: 1 }, {
  expireAfterSeconds: 0,
  name: "ttl_notifications_expires"
});

// ===========================================================================
// notification_deliveries  (queue worker poll)
// ===========================================================================
ensureIndex("notification_deliveries", { status: 1, nextRetryAt: 1 }, { name: "idx_nd_pending_retry" });
ensureIndex("notification_deliveries", { notificationId: 1, createdAt: -1 }, { name: "idx_nd_by_notification" });
ensureIndex("notification_deliveries", { status: 1, createdAt: -1 }, { name: "idx_nd_failed" });
ensureIndex("notification_deliveries", { channel: 1, createdAt: -1, status: 1 }, { name: "idx_nd_channel" });

// ===========================================================================
// payment_transactions  (+ TTL cho link VNPay het han)
// ===========================================================================
ensureIndex("payment_transactions", { orderId: 1, status: 1 }, { name: "idx_pt_order_status" });
// LUU Y: dung PARTIAL (khong dung sparse) vi sparse chi bo qua field THIEU,
// con gia tri null van duoc danh index -> nhieu null se vi pham unique.
ensureIndex("payment_transactions", { transactionCode: 1 }, {
  unique: true,
  partialFilterExpression: { transactionCode: { $type: "string" } },
  name: "uq_pt_txn_code"
});
ensureIndex("payment_transactions", { expiredAt: 1 }, {
  expireAfterSeconds: 0,
  partialFilterExpression: { status: "PENDING" },
  name: "ttl_pt_pending_expired"
});
ensureIndex("payment_transactions", { status: 1, createdAt: -1 }, { name: "idx_pt_status_created" });

// ===========================================================================
// order_returns / support_tickets
// ===========================================================================
ensureIndex("order_returns", { userId: 1, createdAt: -1 }, { name: "idx_returns_user" });
ensureIndex("order_returns", { orderId: 1 }, { name: "idx_returns_order" });
ensureIndex("order_returns", { subOrderId: 1, orderItemId: 1 }, { name: "idx_returns_item" });
ensureIndex("order_returns", { status: 1, createdAt: -1 }, { name: "idx_returns_status" });

ensureIndex("support_tickets", { userId: 1, createdAt: -1 }, { name: "idx_tickets_user" });
ensureIndex("support_tickets", { status: 1, createdAt: -1 }, { name: "idx_tickets_status" });

// ===========================================================================
// association_rules  (materialized view + TTL cho rule cu)
// ===========================================================================
ensureIndex("association_rules", { bookAId: 1, bookBId: 1 }, { unique: true, name: "uq_rules_pair" });
ensureIndex("association_rules", { bookAId: 1, confidence: -1, lift: -1 }, { name: "idx_rules_recommend" });
ensureIndex("association_rules", { bookBId: 1 }, { name: "idx_rules_reverse" });
ensureIndex("association_rules", { updatedAt: 1 }, {
  expireAfterSeconds: 2592000,   // 30 ngay: rule cu tu dong bi xoa
  name: "ttl_rules_stale"
});

// ===========================================================================
// refresh_tokens / otp_codes  (TTL tu dong don rac - khong can job)
// ===========================================================================
ensureIndex("refresh_tokens", { token: 1 }, { unique: true, name: "uq_refresh_token" });
ensureIndex("refresh_tokens", { userId: 1 }, { name: "idx_refresh_user" });
ensureIndex("refresh_tokens", { expiryDate: 1 }, { expireAfterSeconds: 0, name: "ttl_refresh_expiry" });

ensureIndex("otp_codes", { email: 1, purpose: 1 }, { name: "idx_otp_email" });
ensureIndex("otp_codes", { expiresAt: 1 }, { expireAfterSeconds: 0, name: "ttl_otp_expiry" });

// ===========================================================================
// distributed_locks / counters / daily_stats
// ===========================================================================
ensureIndex("distributed_locks", { expiresAt: 1 }, { name: "idx_locks_expires" });
ensureIndex("distributed_locks", { instanceId: 1 }, { name: "idx_locks_instance" });
ensureIndex("daily_stats", { dateKey: 1, sellerId: 1 }, { unique: true, name: "uq_daily_stats" });

// ===========================================================================
// activity_log (time-series): index tren metaField + timeField
// ===========================================================================
try {
  db.getCollection("activity_log").createIndex({ userId: 1, ts: -1 }, { name: "idx_activity_user_ts" });
  print("  + activity_log -> idx_activity_user_ts");
  db.getCollection("activity_log").createIndex({ action: 1, ts: -1 }, { name: "idx_activity_action_ts" });
  print("  + activity_log -> idx_activity_action_ts");
} catch (e) {
  print("  ! activity_log (" + e.message + ")");
}

// ===========================================================================
// TUY CHON: TTL don gio hang bo hoang (90 ngay)
//   LUU Y: TTL xoa CA document carts -> chi bat khi ban THAT SU muon don.
//   Bat: bo comment dong duoi. Tat: db.carts.dropIndex("ttl_carts_abandoned")
// ===========================================================================
// ensureIndex("carts", { updatedAt: 1 }, { expireAfterSeconds: 7776000, name: "ttl_carts_abandoned" });

// ===========================================================================
// TONG KET: liet ke index + kich thuoc tung collection
// ===========================================================================
print("------------------------------------------------------------");
let totalIndexes = 0;
db.getCollectionNames().sort().forEach(function (c) {
  if (c.indexOf("system.") === 0) return;
  const idx = db.getCollection(c).getIndexes();
  totalIndexes += idx.length;
  print("  " + c + ": " + idx.length + " index");
  idx.forEach(function (i) {
    if (i.name === "_id_") return;
    print("      - " + i.name + (i.unique ? " [UNIQUE]" : "") +
      (i.expireAfterSeconds !== undefined ? " [TTL " + i.expireAfterSeconds + "s]" : "") +
      (i.partialFilterExpression ? " [PARTIAL]" : "") +
      (i.collation ? " [COLLATION " + i.collation.locale + "/" + i.collation.strength + "]" : ""));
  });
});
print("Tong so index (ke ca _id_): " + totalIndexes);
print("=== HOAN TAT 02_indexes.js ===");


