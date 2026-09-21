// ============================================================================
// 06_verify_db.js - KIEM TRA TRANG THAI DATABASE (dung khi demo/nghiem thu)
//   Chay: tools\mongo-run-scripts.bat 06
//   In ra: replica set, so collection, so document, so index, validator, TTL.
// ============================================================================

const DB_NAME = process.env.DB_NAME || "bookom";
db = db.getSiblingDB(DB_NAME);
print("=== 06_verify_db.js | DB = " + db.getName() + " ===");

// 1. Replica set
try {
  const st = rs.status();
  const me = st.members.find(m => m.self);
  print("\n[1] Replica set");
  print("    set     : " + st.set);
  print("    member  : " + (me ? me.name + " = " + me.stateStr : "?"));
  print("    transaction/change stream: " + (st.myState === 1 ? "SAN SANG" : "CHUA SAN SANG"));
} catch (e) {
  print("\n[1] Replica set: KHONG CO (" + e.message + ")");
}

// 2. Collection + document + validator
print("\n[2] Collection / document / validator / index");
const names = db.getCollectionNames().filter(c => c.indexOf("system.") !== 0).sort();
let totalDocs = 0, totalIdx = 0, withValidator = 0;
const infos = [];
names.forEach(function (c) {
  const coll = db.getCollection(c);
  const docs = coll.countDocuments({});
  const idx = coll.getIndexes();
  totalDocs += docs;
  totalIdx += idx.length;
  let hasValidator = false;
  try {
    const opts = db.getCollectionInfos({ name: c })[0].options || {};
    hasValidator = !!opts.validator;
  } catch (e) { hasValidator = false; }
  if (hasValidator) withValidator++;
  infos.push({ c: c, docs: docs, idx: idx.length, v: hasValidator });
});
infos.forEach(function (i) {
  print("    " + (i.c + "                            ").substring(0, 28) +
    i.docs + " doc | " + i.idx + " index" + (i.v ? " | validator" : ""));
});
print("    => " + names.length + " collection | " + totalDocs + " document | " +
  totalIdx + " index | " + withValidator + " collection co validator");

// 3. TTL index
print("\n[3] TTL index (MongoDB tu dong xoa du lieu het han)");
names.forEach(function (c) {
  db.getCollection(c).getIndexes().forEach(function (i) {
    if (i.expireAfterSeconds !== undefined) {
      print("    " + c + "." + i.name + " (expireAfterSeconds=" + i.expireAfterSeconds + ")");
    }
  });
});

// 4. Materialized view / du lieu tong hop da tinh
print("\n[4] Materialized view (tinh bang $merge)");
print("    books co rating.count > 0        : " + db.books.countDocuments({ "rating.count": { $gt: 0 } }));
print("    books co stats.soldCount > 0     : " + db.books.countDocuments({ "stats.soldCount": { $gt: 0 } }));
print("    books co boughtTogether          : " + db.books.countDocuments({ "boughtTogether.0": { $exists: true } }));
print("    books co topReviews              : " + db.books.countDocuments({ "topReviews.0": { $exists: true } }));
print("    users co stats.orderCount > 0    : " + db.users.countDocuments({ "stats.orderCount": { $gt: 0 } }));
print("    users co ml.rfmFrequency         : " + db.users.countDocuments({ "ml.rfmFrequency": { $exists: true } }));
print("    daily_stats                      : " + db.daily_stats.countDocuments({}));
print("    association_rules                : " + db.association_rules.countDocuments({}));

// 5. Dung luong
print("\n[5] Dung luong");
const s = db.stats(1024 * 1024);
print("    dataSize=" + s.dataSize + " MB | storageSize=" + s.storageSize +
  " MB | indexSize=" + s.indexSize + " MB");

print("\n=== HOAN TAT 06_verify_db.js ===");
