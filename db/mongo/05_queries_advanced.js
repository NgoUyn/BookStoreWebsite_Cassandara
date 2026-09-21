// ============================================================================
// 05_queries_advanced.js - ~25 TRUY VAN NANG CAO (aggregation pipeline)
//   Tieu chi 1 (truy van nang cao dap ung ung dung) + tieu chi 3 (GUI tool)
//   Chay: tools\mongo-run-scripts.bat  |  hoac dan tung $stage vao Compass.
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 05_queries_advanced.js | DB = " + db.getName() + " ===");
function title(t) { print("\n===== " + t + " ====="); }

const SOLD = { $in: ["COMPLETED", "DELIVERED", "RETURNED"] };

// ---------------------------------------------------------------------------
title("A1. DASHBOARD 1 REQUEST = 1 PIPELINE ($facet)");
// Thay cho 5-8 query SQL rieng biet: doanh thu theo ngay + top sach + trang thai
const dash = db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, createdAt: { $gte: new Date(Date.now() - 90 * 86400000) } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $ne: "CANCELLED" } } },
  {
    $facet: {
      overview: [{
        $group: {
          _id: null,
          revenue: { $sum: "$subOrders.subTotal" },
          orderCount: { $sum: 1 },
          avgOrder: { $avg: "$subOrders.subTotal" }
        }
      }],
      revenueByDay: [
        { $group: { _id: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } },
                    revenue: { $sum: "$subOrders.subTotal" } } },
        { $sort: { _id: 1 } },
        { $limit: 5 }
      ],
      topSellers: [
        { $group: { _id: "$subOrders.sellerId", revenue: { $sum: "$subOrders.subTotal" },
                    orders: { $sum: 1 } } },
        { $sort: { revenue: -1 } },
        { $limit: 3 }
      ],
      statusBreakdown: [
        { $group: { _id: "$subOrders.status", count: { $sum: 1 } } },
        { $sort: { count: -1 } }
      ],
      topBooks: [
        { $unwind: "$subOrders.items" },
        { $group: { _id: "$subOrders.items.bookId", title: { $first: "$subOrders.items.title" },
                    sold: { $sum: "$subOrders.items.quantity" } } },
        { $sort: { sold: -1 } },
        { $limit: 3 }
      ]
    }
  }
]).toArray()[0];
printjson(dash.overview);
print("Top seller:"); dash.topSellers.forEach(s => print("  seller#" + s._id + " | " + s.revenue + "d | " + s.orders + " don"));
print("Trang thai:"); dash.statusBreakdown.forEach(s => print("  " + s._id + ": " + s.count));
print("Top sach ban chay:"); dash.topBooks.forEach(b => print("  #" + b._id + " " + String(b.title).substring(0, 38) + " | " + b.sold));

// ---------------------------------------------------------------------------
title("A2. Doanh thu theo DANH MUC ($unwind 2 cap + $group)");
printjson(db.orders.aggregate([
  { $match: { isDeleted: { $ne: true } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $ne: "CANCELLED" } } },
  { $unwind: "$subOrders.items" },
  {
    $lookup: {                                   // join sang books de lay danh muc
      from: "books", localField: "subOrders.items.bookId",
      foreignField: "_id", as: "book"
    }
  },
  { $unwind: "$book" },
  {
    $group: {
      _id: "$book.categoryName",
      revenue: { $sum: { $multiply: ["$subOrders.items.quantity", "$subOrders.items.unitPrice"] } },
      unitsSold: { $sum: "$subOrders.items.quantity" }
    }
  },
  { $sort: { revenue: -1 } },
  { $limit: 6 }
]).toArray());

// ---------------------------------------------------------------------------
title("A3. $lookup CO PIPELINE + let: lay 3 review moi nhat cho tung sach");
printjson(db.books.aggregate([
  { $match: { "rating.count": { $gt: 0 } } },
  { $sort: { "rating.avg": -1, "rating.count": -1 } },
  { $limit: 2 },
  {
    $lookup: {
      from: "reviews",
      let: { bookId: "$_id" },
      pipeline: [
        { $match: { $expr: { $eq: ["$bookId", "$$bookId"] }, "moderation.status": "VISIBLE" } },
        { $sort: { createdAt: -1 } },
        { $limit: 3 },
        { $project: { _id: 0, rating: 1, "user.username": 1, comment: 1 } }
      ],
      as: "latestReviews"
    }
  },
  { $project: { title: 1, "rating.avg": 1, "rating.count": 1, latestReviews: 1 } }
]).toArray());

// ---------------------------------------------------------------------------
title("A4. CAY DANH MUC bang $graphLookup (danh muc cha -> con -> chau)");
printjson(db.categories.aggregate([
  { $match: { parentId: null } },
  { $limit: 2 },
  {
    $graphLookup: {
      from: "categories",
      startWith: "$_id",
      connectFromField: "_id",
      connectToField: "parentId",
      as: "descendants",
      maxDepth: 3,
      depthField: "level"
    }
  },
  { $project: { name: 1, "descendants.name": 1, "descendants.level": 1 } }
]).toArray());

// ---------------------------------------------------------------------------
title("A5. Phan khuc gia tri don hang ($bucket)");
printjson(db.orders.aggregate([
  { $match: { isDeleted: { $ne: true } } },
  {
    $bucket: {
      groupBy: "$totalAmount",
      boundaries: [0, 200000, 500000, 1000000, 2000000, 10000000],
      default: "Khac",
      output: {
        soDon: { $sum: 1 },
        doanhThu: { $sum: "$totalAmount" },
        trungBinh: { $avg: "$totalAmount" }
      }
    }
  }
]).toArray());

// ---------------------------------------------------------------------------
title("A6. Chia nhom sach theo gia tu dong ($bucketAuto)");
printjson(db.books.aggregate([
  { $match: { approvalStatus: "APPROVED" } },
  {
    $bucketAuto: {
      groupBy: "$finalPrice",
      buckets: 5,
      output: { soSach: { $sum: 1 }, giaThapNhat: { $min: "$finalPrice" }, giaCaoNhat: { $max: "$finalPrice" } }
    }
  }
]).toArray());

// ---------------------------------------------------------------------------
title("A7. WINDOW FUNCTION: trung binh dong 7 ngay + xep hang ($setWindowFields)");
printjson(db.daily_stats.aggregate([
  { $group: { _id: "$dateKey", revenue: { $sum: "$revenue" } } },
  { $sort: { _id: 1 } },
  {
    $setWindowFields: {
      sortBy: { _id: 1 },
      output: {
        movingAvg7d: {
          $avg: "$revenue",
          window: { documents: [-6, 0] }        // 6 ngay truoc + ngay hien tai
        },
        xepHangDoanhThu: { $rank: {} }
      }
    }
  },
  { $sort: { _id: -1 } },
  { $limit: 5 }
]).toArray());

// ---------------------------------------------------------------------------
title("A8. RFM SEGMENTATION: phan khuc khach hang theo Recency/Frequency/Monetary");
const rfm = db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  {
    $group: {
      _id: "$buyerId",
      lastOrder: { $max: "$createdAt" },
      frequency: { $sum: 1 },
      monetary: { $sum: "$totalAmount" }
    }
  },
  {
    $set: {
      recencyDays: { $divide: [{ $subtract: ["$$NOW", "$lastOrder"] }, 86400000] }
    }
  },
  {
    $set: {
      segment: {
        $switch: {
          branches: [
            { case: { $and: [{ $lte: ["$recencyDays", 30] }, { $gte: ["$monetary", 1000000] }] },
              then: "VIP" },
            { case: { $and: [{ $lte: ["$recencyDays", 90] }, { $gte: ["$frequency", 2] }] },
              then: "TRUNG_THANH" },
            { case: { $gte: ["$recencyDays", 180] }, then: "NGUY_CO_ROI" },
            { case: { $gte: ["$recencyDays", 90] }, then: "CAN_CHAM_SOC" }
          ],
          default: "MOI"
        }
      }
    }
  },
  { $group: { _id: "$segment", soKhach: { $sum: 1 }, doanhThu: { $sum: "$monetary" },
              tbDon: { $avg: "$frequency" } } },
  { $sort: { soKhach: -1 } }
]).toArray();
printjson(rfm);

// ---------------------------------------------------------------------------
title("A9. $merge: ghi phan khuc RFM vao users.ml (materialized view)");
db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  { $group: { _id: "$buyerId", rfmFrequency: { $sum: 1 }, rfmMonetary: { $sum: "$totalAmount" },
              rfmLastOrder: { $max: "$createdAt" } } },
  { $merge: { into: "users", on: "_id",
              whenMatched: [{ $set: {
                "ml.rfmFrequency": "$$new.rfmFrequency",
                "ml.rfmMonetary": "$$new.rfmMonetary",
                "ml.rfmLastOrder": "$$new.rfmLastOrder"
              } }],
              whenNotMatched: "discard" } }
]).toArray();
print("-> da ghi RFM vao users.ml. Vi du:");
printjson(db.users.find({ "ml.rfmFrequency": { $gte: 3 } },
  { username: 1, "ml.rfmFrequency": 1, "ml.rfmMonetary": 1, _id: 0 }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("A10. $unionWith: doanh thu THUAN = ban - tra hang");
printjson(db.orders.aggregate([
  { $match: { isDeleted: { $ne: true }, status: { $ne: "CANCELLED" } } },
  { $unwind: "$subOrders" },
  { $match: { "subOrders.status": { $ne: "CANCELLED" } } },
  { $group: { _id: "ban", amount: { $sum: "$subOrders.subTotal" } } },
  {
    $unionWith: {
      coll: "order_returns",
      pipeline: [
        { $match: { status: { $in: ["APPROVED", "REFUNDED"] } } },
        { $group: { _id: "tra", amount: { $sum: "$quantityReturned" } } }
      ]
    }
  }
]).toArray());

// ---------------------------------------------------------------------------
title("A11. $densify + $fill: dien ngay THIEU doanh thu bang 0");
// LUU Y: $densify chi nhan field numeric hoac DATE -> phai $dateFromString truoc
const filled = db.daily_stats.aggregate([
  { $group: { _id: "$dateKey", revenue: { $sum: "$revenue" } } },
  { $set: { ngay: { $dateFromString: { dateString: "$_id" } } } },
  { $sort: { ngay: 1 } },
  { $densify: { field: "ngay", range: { step: 1, unit: "day", bounds: "full" } } },
  { $fill: { output: { revenue: { value: 0 } } } },
  { $project: { _id: 0, ngay: { $dateToString: { format: "%Y-%m-%d", date: "$ngay" } }, revenue: 1 } },
  { $sort: { ngay: -1 } }
]).toArray();
print("  tong so ngay sau khi densify: " + filled.length +
  " | so ngay doanh thu = 0: " + filled.filter(d => d.revenue === 0).length);
printjson(filled.slice(0, 4));

// ---------------------------------------------------------------------------
title("A12. FULL-TEXT SEARCH co TRONG SO + diem lien quan ($meta textScore)");
printjson(db.books.find(
  { $text: { $search: "\"khoi nghiep\" dao tao" }, approvalStatus: "APPROVED" },
  { score: { $meta: "textScore" }, title: 1, author: 1 }
).sort({ score: { $meta: "textScore" } }).limit(3).toArray());

// ---------------------------------------------------------------------------
title("A13. $geoNear: tim shop GAN vi tri nguoi dung (2dsphere)");
printjson(db.seller_shops.aggregate([
  {
    $geoNear: {
      near: { type: "Point", coordinates: [108.2022, 16.0544] },   // Da Nang
      distanceField: "distanceMeters",
      maxDistance: 500000,
      spherical: true
    }
  },
  { $project: { shopName: 1, city: 1,
                distanceKm: { $round: [{ $divide: ["$distanceMeters", 1000] }, 1] } } },
  { $limit: 5 }
]).toArray());

// ---------------------------------------------------------------------------
title("A14. TTL index: MongoDB TU DONG xoa du lieu het han (khong can job)");
db.getCollectionNames().filter(c => c.indexOf("system.") !== 0).forEach(function (c) {
  db.getCollection(c).getIndexes().forEach(function (i) {
    if (i.expireAfterSeconds !== undefined) {
      print("  " + c + " -> " + i.name + " (TTL " + i.expireAfterSeconds + "s)");
    }
  });
});
print("  refresh_tokens da het han (cho TTL don): " +
  db.refresh_tokens.countDocuments({ expiryDate: { $lt: new Date() } }));

// ---------------------------------------------------------------------------
title("A15. MULTI-DOCUMENT TRANSACTION (session): xu ly dat hang an toan");
const session = db.getMongo().startSession();
const sdb = session.getDatabase(db.getName());
const txBook = db.books.findOne({ stockQuantity: { $gte: 5 }, approvalStatus: "APPROVED" },
  { stockQuantity: 1, title: 1 });
const beforeStock = txBook.stockQuantity;

// (1) Giao dich THANH CONG: tru kho + tang luot ban
session.startTransaction();
sdb.books.updateOne({ _id: txBook._id }, { $inc: { stockQuantity: -2, "stats.soldCount": 2 } });
session.commitTransaction();
print("  [commit] ton kho: " + beforeStock + " -> " +
  db.books.findOne({ _id: txBook._id }).stockQuantity + " (da tru 2)");

// (2) Giao dich BI HUY: mo phong loi giua chung -> du lieu quay ve nhu cu
session.startTransaction();
sdb.books.updateOne({ _id: txBook._id }, { $inc: { stockQuantity: -3 } });
print("  [trong transaction] ton kho tam thoi = " +
  sdb.books.findOne({ _id: txBook._id }).stockQuantity);
session.abortTransaction();
print("  [sau abort] ton kho = " + db.books.findOne({ _id: txBook._id }).stockQuantity +
  " => ROLLBACK thanh cong (du lieu khong bi mat)");
session.endSession();

// ---------------------------------------------------------------------------
title("A16. UPDATE BANG AGGREGATION PIPELINE: tinh lai rating trong 1 thao tac");
const oneBook = db.reviews.findOne({}).bookId;
const stat = db.reviews.aggregate([
  { $match: { bookId: oneBook, "moderation.status": "VISIBLE" } },
  { $group: { _id: null, avg: { $avg: "$rating" }, count: { $sum: 1 } } }
]).toArray()[0];
db.books.updateOne({ _id: oneBook }, [
  {
    $set: {
      "rating.avg": { $round: [stat.avg, 2] },
      "rating.count": stat.count,
      "rating.recomputedBy": "aggregation-pipeline",
      updatedAt: "$$NOW"
    }
  }
]);
print("  sach #" + oneBook + " -> rating = ");
printjson(db.books.findOne({ _id: oneBook }, { rating: 1, _id: 0 }));

// ---------------------------------------------------------------------------
title("A17. Toan tu mang: $filter / $slice / $push+$slice tren mang nhung");
printjson(db.orders.aggregate([
  { $match: { buyerId: db.orders.findOne({}).buyerId } },
  { $limit: 1 },
  { $unwind: "$subOrders" },
  {
    $project: {
      orderCode: 1,
      subOrderId: "$subOrders.subOrderId",
      itemDatTien: {
        $filter: {
          input: "$subOrders.items", as: "i",
          cond: { $gte: ["$$i.unitPrice", 100000] }
        }
      },
      haiItemDau: { $slice: ["$subOrders.items", 2] }
    }
  }
]).toArray());

const rv = db.reviews.findOne({});
db.reviews.updateOne({ _id: rv._id }, {
  $push: {
    replies: {
      $each: [{ replyId: 999999, userId: 2, content: "Cam on ban da danh gia!",
                createdAt: new Date() }],
      $slice: -20            // LUON giu 20 reply moi nhat -> mang KHONG phinh vo han
    }
  }
});
print("  review #" + rv._id + " sau $push + $slice(-20): " +
  db.reviews.findOne({ _id: rv._id }).replies.length + " reply");

// ---------------------------------------------------------------------------
title("A18. EXPLAIN: COLLSCAN vs IXSCAN (chung minh index co tac dung)");
function planSummary(exp) {
  const planText = JSON.stringify(exp.queryPlanner.winningPlan);
  const st = exp.executionStats;
  const stage = planText.indexOf("IXSCAN") >= 0 ? "IXSCAN (dung index)"
    : (planText.indexOf("COLLSCAN") >= 0 ? "COLLSCAN (quet toan bo)" : "khac");
  return "  " + stage + " | tra ve=" + st.nReturned + " | doc=" + st.totalDocsExamined +
    " | thoiGian=" + st.executionTimeMillis + "ms";
}
print("Query CO index (idx_books_price_rating + idx_books_catalog):");
print(planSummary(db.books.find({ approvalStatus: "APPROVED",
  finalPrice: { $gte: 100000, $lte: 120000 } }).explain("executionStats")));
print("Query KHONG index (regex khong anchor tren description):");
print(planSummary(db.books.find({ description: /chuyen sau/ })
  .limit(50).explain("executionStats")));
print("=> Chen them .hint(\"idx_books_catalog\") de ep dung index khi can.");

// ---------------------------------------------------------------------------
title("A19. VALIDATOR $jsonSchema: chan du lieu sai ngay tai DB");
try {
  db.books.insertOne({ title: "Sach thieu truong bat buoc" });   // thieu author/price/...
  print("  !! Khong bi chan - kiem tra lai validator");
} catch (e) {
  print("  OK, MongoDB da chan: " + String(e.message).substring(0, 110));
}
try {
  db.reviews.insertOne({ bookId: 10001, userId: 101, rating: 9, createdAt: new Date() });
  print("  !! rating=9 khong bi chan - kiem tra lai validator");
} catch (e) {
  print("  OK, chan rating ngoai 1..5: " + String(e.message).substring(0, 90));
}

// ---------------------------------------------------------------------------
title("A20. QUERY PROFILER: tim truy van cham tu system.profile");
db.setProfilingLevel(1, { slowms: 0 });
db.books.find({ description: /chuyen sau/ }).limit(5).toArray();      // quet toan bo
db.orders.countDocuments({ "subOrders.sellerId": 37 });               // co index
const prof = db.system.profile.find({}).sort({ ts: -1 }).limit(3).toArray();
print("  so ban ghi profile: " + prof.length);
prof.forEach(function (p) {
  print("   op=" + p.op + " | " + p.millis + "ms | docsExamined=" + (p.docsExamined || 0) +
        " | plan=" + (p.planSummary || "-"));
});
db.setProfilingLevel(0);
print("  da tat profiler");

// ---------------------------------------------------------------------------
title("A21. CHANGE STREAM: lang nghe thay doi realtime");
const hello = db.adminCommand({ hello: 1 });
print("  replica set       : " + (hello.setName ? "CO (set=" + hello.setName + ")" : "KHONG"));
print("  transaction (sessions): " +
  (hello.logicalSessionTimeoutMinutes ? "CO - ho tro multi-document transaction" : "KHONG"));
print("  oplog/change stream   : " + (hello.setName && hello.logicalSessionTimeoutMinutes
  ? "SAN SANG (change stream yeu cau replica set)"
  : "KHONG - can chay mongod voi --replSet"));
print("  Vi du Java: db.books.watch([...]) -> push SSE realtime cho client");
print("  Demo truc tiep (chay o CUOI script, dang bat dong bo) ...");

// ---------------------------------------------------------------------------
title("A22. VAN HANH: replica set + thong ke thao tac");
const rsStatus = rs.status();
print("  set=" + rsStatus.set + " | members=" +
  rsStatus.members.map(m => m.name + "=" + m.stateStr).join(", "));
const ss = db.serverStatus();
print("  uptime=" + ss.uptime + "s | insert=" + ss.opcounters.insert +
  " | query=" + ss.opcounters.query + " | update=" + ss.opcounters.update +
  " | delete=" + ss.opcounters.delete);
const dbStat = db.stats(1024 * 1024);
print("  collections=" + Object.keys(dbStat.collections || {}).length +
  " | dataSize=" + dbStat.dataSize + " MB | indexSize=" + dbStat.indexSize + " MB");

print("\n=== HOAN TAT 05_queries_advanced.js ===");

// ===========================================================================
// DEMO CHANGE STREAM (bat dong bo - dat o CUOI de khong chan cac query tren)
//   Trong mongosh TUONG TAC ban co the chay truc tiep:
//     const cs = db.books.watch(); await cs.tryNext();
// ===========================================================================
(async function demoChangeStream() {
  try {
    const cs = db.books.watch(
      [{ $match: { operationType: { $in: ["insert", "update"] } } }],
      { maxAwaitTimeMS: 3000 });
    const tmpId = db.counters.findOne({ _id: "books" }).seq + 1;
    db.books.insertOne({
      _id: tmpId, title: "Sach test change stream", author: "Test", price: 1000,
      approvalStatus: "PENDING", isActive: true, createdAt: new Date()
    });
    const ev = await cs.tryNext();
    if (ev) {
      print("[change stream] event: " + ev.operationType + " | _id=" + ev.documentKey._id +
            " | ns=" + ev.ns.db + "." + ev.ns.coll);
    } else {
      print("[change stream] khong nhan duoc event (kiem tra replica set)");
    }
    await cs.close();
    db.books.deleteOne({ _id: tmpId });
    print("[change stream] da don du lieu test");
  } catch (e) {
    print("[change stream] loi: " + e.message);
  }
})();

