// ============================================================================
// 01_create_collections_validators.js
// Tao database + 20 collection cua BOOKOM voi VALIDATOR ($jsonSchema).
//
// Mo hinh: 6 AGGREGATE ROOT (tu chua con embedded)
//   1. users          - profile + addresses[] + wishlist + ml + seller + stats
//   2. books          - images + rating(summary) + stats + tags + boughtTogether[]
//   3. carts          - items[]  (1 document / buyer)
//   4. orders         - subOrders[].items[] + shipping + payment + voucher
//   5. seller_shops   - seller snapshot + location(geo) + stats
//   6. reviews        - images[] + replies[] + moderation.history[]
// 12 COLLECTION THAM CHIEU: categories, coupons, notifications,
//   notification_deliveries, payment_transactions, order_returns,
//   support_tickets, association_rules, refresh_tokens, otp_codes,
//   activity_log (time-series), distributed_locks
// 2 HA TANG: counters, daily_stats
//
// CACH CHAY:
//   tools\mongo-run-scripts.bat
//   hoac: mongosh mongodb://127.0.0.1:27018/bookom?replicaSet=rs0 --file db/mongo/01_create_collections_validators.js
//
// IDEMPOTENT: tao moi neu chua co, cap nhat validator neu da co.
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 01_create_collections_validators.js | DB = " + db.getName() + " ===");

// ---------------------------------------------------------------------------
// Helper: tao collection neu chua co, nguoc lai cap nhat validator (collMod)
// ---------------------------------------------------------------------------
function ensureCollection(name, options) {
  const opts = options || {};
  if (db.getCollectionNames().indexOf(name) !== -1) {
    const mod = { collMod: name };
    if (opts.validator) mod.validator = opts.validator;
    if (opts.validationLevel) mod.validationLevel = opts.validationLevel;
    if (opts.validationAction) mod.validationAction = opts.validationAction;
    try {
      db.runCommand(mod);
      print("  ~ cap nhat validator: " + name);
    } catch (e) {
      print("  ! khong cap nhat duoc " + name + " (" + e.message + ")");
    }
    return;
  }
  db.createCollection(name, opts);
  print("  + tao moi: " + name);
}

// Helper tao schema gon: v(required[], properties{})
function v(required, properties) {
  return { $jsonSchema: { bsonType: "object", required: required, properties: properties } };
}

// ===========================================================================
// 1. AGGREGATE ROOT: users
// ===========================================================================
ensureCollection("users", {
  validator: v(["username", "passwordHash", "role", "isActive", "createdAt"], {
    username: { bsonType: "string" },
    passwordHash: { bsonType: "string" },
    role: { enum: ["ADMIN", "SELLER", "BUYER"] },
    isActive: { bsonType: "bool" },
    createdAt: { bsonType: "date" },
    updatedAt: { bsonType: ["date", "null"] },
    email: { bsonType: ["string", "null"] },
    phone: { bsonType: ["string", "null"] },
    profile: { bsonType: ["object", "null"] },
    addresses: { bsonType: ["array", "null"] },
    wishlist: { bsonType: ["object", "null"] },
    favoriteCategoryIds: { bsonType: ["array", "null"] },
    seller: { bsonType: ["object", "null"] },
    ml: { bsonType: ["object", "null"] },
    stats: { bsonType: ["object", "null"] },
    security: { bsonType: ["object", "null"] },
    schemaVersion: { bsonType: ["int", "long", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 2. AGGREGATE ROOT: books
// ===========================================================================
ensureCollection("books", {
  validator: v(["title", "author", "price", "approvalStatus", "isActive"], {
    title: { bsonType: "string" },
    author: { bsonType: "string" },
    publisher: { bsonType: ["string", "null"] },
    publishYear: { bsonType: ["int", "long", "null"] },
    isbn: { bsonType: ["string", "null"] },
    description: { bsonType: ["string", "null"] },
    price: { bsonType: ["double", "int", "long", "decimal"] },
    discountAmount: { bsonType: ["int", "long", "null"] },
    finalPrice: { bsonType: ["double", "int", "long", "decimal", "null"] },
    stockQuantity: { bsonType: ["int", "long", "null"] },
    images: { bsonType: ["object", "null"] },
    coverFileId: { bsonType: ["objectId", "null"] },
    categoryId: { bsonType: ["int", "long", "null"] },
    categoryName: { bsonType: ["string", "null"] },
    categoryPath: { bsonType: ["array", "null"] },
    sellerId: { bsonType: ["int", "long", "null"] },
    seller: { bsonType: ["object", "null"] },
    tags: { bsonType: ["array", "null"] },
    rating: { bsonType: ["object", "null"] },
    stats: { bsonType: ["object", "null"] },
    topReviews: { bsonType: ["array", "null"] },
    boughtTogether: { bsonType: ["array", "null"] },
    approvalStatus: { enum: ["PENDING", "APPROVED", "REJECTED"] },
    isActive: { bsonType: "bool" },
    isPinned: { bsonType: "bool" },
    schemaVersion: { bsonType: ["int", "long", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 3. AGGREGATE ROOT: carts  (1 document / buyer, items[] embedded)
// ===========================================================================
ensureCollection("carts", {
  validator: v(["buyerId", "items"], {
    buyerId: { bsonType: ["int", "long"] },
    items: {
      bsonType: "array",
      items: {
        bsonType: "object",
        required: ["bookId", "quantity"],
        properties: {
          bookId: { bsonType: ["int", "long"] },
          sellerId: { bsonType: ["int", "long", "null"] },
          quantity: { bsonType: "int", minimum: 1 },
          unitPrice: { bsonType: ["double", "int", "long", "null"] }
        }
      }
    },
    totals: { bsonType: ["object", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 4. AGGREGATE ROOT: orders  (subOrders[].items[] + shipping + payment embed)
// ===========================================================================
ensureCollection("orders", {
  validator: v(["orderCode", "buyerId", "totalAmount", "createdAt"], {
    orderCode: { bsonType: "string" },
    buyerId: { bsonType: ["int", "long"] },
    buyer: { bsonType: ["object", "null"] },
    itemsTotal: { bsonType: ["double", "int", "long", "null"] },
    discountAmount: { bsonType: ["double", "int", "long", "null"] },
    shippingFee: { bsonType: ["double", "int", "long", "null"] },
    totalAmount: { bsonType: ["double", "int", "long"] },
    couponCode: { bsonType: ["string", "null"] },
    voucher: { bsonType: ["object", "null"] },
    shipping: { bsonType: ["object", "null"] },
    payment: { bsonType: ["object", "null"] },
    status: { bsonType: ["string", "null"] },
    subOrders: {
      bsonType: ["array", "null"],
      items: {
        bsonType: "object",
        required: ["subOrderId", "sellerId", "status", "subTotal"],
        properties: {
          subOrderId: { bsonType: ["int", "long"] },
          sellerId: { bsonType: ["int", "long"] },
          seller: { bsonType: ["object", "null"] },
          status: {
            enum: ["PENDING", "PROCESSING", "SHIPPING", "DELIVERED",
                   "COMPLETED", "CANCELLED", "RETURNED"]
          },
          subTotal: { bsonType: ["double", "int", "long"] },
          items: { bsonType: ["array", "null"] },
          statusHistory: { bsonType: ["array", "null"] }
        }
      }
    },
    isDeleted: { bsonType: ["bool", "null"] },
    schemaVersion: { bsonType: ["int", "long", "null"] },
    createdAt: { bsonType: "date" },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 5. AGGREGATE ROOT: seller_shops  (seller snapshot + geo + stats)
// ===========================================================================
ensureCollection("seller_shops", {
  validator: v(["sellerId", "slug", "shopName", "city", "approvalStatus"], {
    sellerId: { bsonType: ["int", "long"] },
    slug: { bsonType: "string" },
    shopName: { bsonType: "string" },
    description: { bsonType: ["string", "null"] },
    seller: { bsonType: ["object", "null"] },
    logoUrl: { bsonType: ["string", "null"] },
    bannerUrl: { bsonType: ["string", "null"] },
    contactEmail: { bsonType: ["string", "null"] },
    contactPhone: { bsonType: ["string", "null"] },
    address: { bsonType: ["string", "null"] },
    city: { bsonType: "string" },
    province: { bsonType: ["string", "null"] },
    location: { bsonType: ["object", "null"] },
    rejectionReason: { bsonType: ["string", "null"] },
    approvalStatus: { enum: ["PENDING", "APPROVED", "REJECTED"] },
    followerCount: { bsonType: ["int", "long", "null"] },
    rating: { bsonType: ["double", "int", "null"] },
    ratingCount: { bsonType: ["int", "long", "null"] },
    stats: { bsonType: ["object", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 6. AGGREGATE ROOT: reviews  (images[] + replies[] + moderation.history[])
//    LUU Y: day la aggregate RIENG, KHONG embed vao books (quan he 1-N
//    khong tran + bi truy van cat ngang theo ca bookId va userId).
// ===========================================================================
ensureCollection("reviews", {
  validator: v(["bookId", "userId", "rating", "createdAt"], {
    bookId: { bsonType: ["int", "long"] },
    book: { bsonType: ["object", "null"] },
    userId: { bsonType: ["int", "long"] },
    user: { bsonType: ["object", "null"] },
    orderId: { bsonType: ["int", "long", "null"] },
    isVerifiedPurchase: { bsonType: ["bool", "null"] },
    rating: { bsonType: "int", minimum: 1, maximum: 5 },
    comment: { bsonType: ["string", "null"] },
    images: { bsonType: ["array", "null"] },
    replies: { bsonType: ["array", "null"] },
    helpfulCount: { bsonType: ["int", "long", "null"] },
    moderation: { bsonType: ["object", "null"] },
    flags: { bsonType: ["object", "null"] },
    createdAt: { bsonType: "date" },
    updatedAt: { bsonType: ["date", "null"] },
    schemaVersion: { bsonType: ["int", "long", "null"] }
  }),
  validationLevel: "moderate",
  validationAction: "error"
});

// ===========================================================================
// 7-13. COLLECTION THAM CHIEU (reference) - aggregate khac tro toi bang id
// ===========================================================================
ensureCollection("categories", {
  validator: v(["name", "slug"], {
    name: { bsonType: "string" },
    slug: { bsonType: "string" },
    description: { bsonType: ["string", "null"] },
    parentId: { bsonType: ["int", "long", "null"] },
    path: { bsonType: ["array", "null"] },
    bookCount: { bsonType: ["int", "long", "null"] },
    sortOrder: { bsonType: ["int", "long", "null"] },
    isActive: { bsonType: ["bool", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("coupons", {
  validator: v(["code", "type", "discountValue", "isActive"], {
    code: { bsonType: "string" },
    type: { enum: ["FIXED", "PERCENT"] },
    discountValue: { bsonType: ["int", "long"] },
    scope: { enum: ["GLOBAL", "SELLER", null] },
    sellerId: { bsonType: ["int", "long", "null"] },
    description: { bsonType: ["string", "null"] },
    minOrderAmount: { bsonType: ["int", "long", "null"] },
    maxDiscountAmount: { bsonType: ["double", "int", "null"] },
    startDate: { bsonType: ["date", "null"] },
    expiresAt: { bsonType: ["date", "null"] },
    totalQuantity: { bsonType: ["int", "long", "null"] },
    usedCount: { bsonType: ["int", "long", "null"] },
    perUserLimit: { bsonType: ["int", "long", "null"] },
    isActive: { bsonType: "bool" },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("notifications", {
  validator: v(["userId", "type", "title", "isRead", "createdAt"], {
    userId: { bsonType: ["int", "long"] },
    type: { bsonType: "string" },
    title: { bsonType: "string" },
    message: { bsonType: ["string", "null"] },
    payload: { bsonType: ["object", "null"] },
    isRead: { bsonType: "bool" },
    priority: { enum: ["LOW", "NORMAL", "HIGH", "URGENT", null] },
    readAt: { bsonType: ["date", "null"] },
    expiresAt: { bsonType: ["date", "null"] },
    createdAt: { bsonType: "date" }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("notification_deliveries", {
  validator: v(["notificationId", "channel", "status", "attemptCount"], {
    notificationId: { bsonType: ["int", "long"] },
    userId: { bsonType: ["int", "long", "null"] },
    channel: { enum: ["SSE", "EMAIL", "PUSH", "SMS"] },
    status: { enum: ["PENDING", "SENT", "FAILED", "DROPPED"] },
    attemptCount: { bsonType: ["int", "long"] },
    nextRetryAt: { bsonType: ["date", "null"] },
    lastError: { bsonType: ["string", "null"] },
    sentAt: { bsonType: ["date", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("payment_transactions", {
  validator: v(["orderId", "amount", "method", "status"], {
    orderId: { bsonType: ["int", "long"] },
    amount: { bsonType: ["int", "long", "double"] },
    method: { enum: ["COD", "VNPAY", "MOMO", "BANK_TRANSFER"] },
    status: { enum: ["PENDING", "SUCCESS", "FAILED", "CANCELLED", "EXPIRED", "REFUNDED"] },
    transactionCode: { bsonType: ["string", "null"] },
    paymentUrl: { bsonType: ["string", "null"] },
    responseCode: { bsonType: ["string", "null"] },
    responseMessage: { bsonType: ["string", "null"] },
    ipnPayload: { bsonType: ["object", "null"] },
    failureReason: { bsonType: ["string", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    paidAt: { bsonType: ["date", "null"] },
    expiredAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("order_returns", {
  validator: v(["orderId", "subOrderId", "orderItemId", "userId", "quantityReturned", "status"], {
    orderId: { bsonType: ["int", "long"] },
    subOrderId: { bsonType: ["int", "long"] },
    orderItemId: { bsonType: ["int", "long"] },
    userId: { bsonType: ["int", "long"] },
    quantityReturned: { bsonType: ["int", "long"] },
    reason: { enum: ["DEFECTIVE", "WRONG_ITEM", "CHANGE_MIND", "OTHER", null] },
    status: { enum: ["PENDING", "APPROVED", "REJECTED", "REFUNDED"] },
    itemSnapshot: { bsonType: ["object", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    processedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("support_tickets", {
  validator: v(["userId", "subject", "status"], {
    userId: { bsonType: ["int", "long"] },
    subject: { bsonType: "string" },
    description: { bsonType: ["string", "null"] },
    status: { enum: ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"] },
    priority: { enum: ["LOW", "NORMAL", "HIGH", "URGENT", null] },
    createdAt: { bsonType: ["date", "null"] },
    resolvedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("association_rules", {
  validator: v(["bookAId", "bookBId", "support", "confidence", "lift"], {
    bookAId: { bsonType: ["int", "long"] },
    bookBId: { bsonType: ["int", "long"] },
    support: { bsonType: ["double", "int", "decimal"] },
    confidence: { bsonType: ["double", "int", "decimal"] },
    lift: { bsonType: ["double", "int", "decimal"] },
    transactionCount: { bsonType: ["int", "long", "null"] },
    windowDays: { bsonType: ["int", "long", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("refresh_tokens", {
  validator: v(["userId", "token", "expiryDate"], {
    userId: { bsonType: ["int", "long"] },
    token: { bsonType: "string" },
    expiryDate: { bsonType: "date" },
    createdAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("otp_codes", {
  validator: v(["email", "code", "expiresAt"], {
    email: { bsonType: "string" },
    code: { bsonType: "string" },
    purpose: { bsonType: ["string", "null"] },
    attempts: { bsonType: ["int", "long", "null"] },
    verified: { bsonType: ["bool", "null"] },
    createdAt: { bsonType: ["date", "null"] },
    expiresAt: { bsonType: "date" }
  }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("distributed_locks", {
  validator: v(["holderId", "expiresAt"], {
    holderId: { bsonType: "string" },
    instanceId: { bsonType: ["string", "null"] },
    acquiredAt: { bsonType: ["date", "null"] },
    heartbeatAt: { bsonType: ["date", "null"] },
    expiresAt: { bsonType: "date" }
  }),
  validationLevel: "moderate", validationAction: "error"
});

// ===========================================================================
// 14-15. HA TANG: counters (cap Long id) + daily_stats (materialized view)
// ===========================================================================
ensureCollection("counters", {
  validator: v(["seq"], { seq: { bsonType: ["int", "long"] } }),
  validationLevel: "moderate", validationAction: "error"
});

ensureCollection("daily_stats", {
  validator: v(["dateKey", "revenue", "orderCount"], {
    dateKey: { bsonType: "string" },
    sellerId: { bsonType: ["int", "long", "null"] },
    revenue: { bsonType: ["double", "int", "long"] },
    orderCount: { bsonType: ["int", "long"] },
    unitsSold: { bsonType: ["int", "long", "null"] },
    newBuyers: { bsonType: ["int", "long", "null"] },
    topCategories: { bsonType: ["array", "null"] },
    updatedAt: { bsonType: ["date", "null"] }
  }),
  validationLevel: "moderate", validationAction: "error"
});

// ===========================================================================
// 16. activity_log - TIME-SERIES collection (TTL 180 ngay)
//    Time-series KHONG doi duoc option sau khi tao -> chi tao neu chua co.
// ===========================================================================
if (db.getCollectionNames().indexOf("activity_log") === -1) {
  db.createCollection("activity_log", {
    timeseries: { timeField: "ts", metaField: "userId", granularity: "seconds" },
    expireAfterSeconds: 15552000   // 180 ngay
  });
  print("  + tao moi (time-series): activity_log");
} else {
  print("  = da co (time-series): activity_log");
}

// ===========================================================================
// TONG KET
// ===========================================================================
const cols = db.getCollectionNames().sort();
print("------------------------------------------------------------");
print("Tong so collection: " + cols.length);
cols.forEach(function (c) { print("  - " + c); });
print("------------------------------------------------------------");
print("=== HOAN TAT 01_create_collections_validators.js ===");

