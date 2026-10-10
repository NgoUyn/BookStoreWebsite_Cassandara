// ============================================================================
// 14_repair_shop_seller_snapshot.js - VA DU LIEU LEGACY: shop thieu seller.id
//
//   Trieu chung:
//     PUT /api/admin/seller-shops-management/{shopId}/approve -> HTTP 500.
//   Nguyen nhan:
//     Document shop cu (vd _id=1 "Nha xuat ban Tre") chi luu `sellerId` o cap
//     document, KHONG co `seller.id` trong snapshot nguoi ban -> controller
//     resolve shop.getSeller().getId() = null -> userRepository.findById(null)
//     nem IllegalArgumentException.
//
//   Cach va:
//     Copy `sellerId` (field top-level) vao `seller.id` bang aggregation
//     pipeline ($set: {'seller.id': '$sellerId'}) de giu nguyen kieu Long.
//   Idempotent: chi update document CON THIEU seller.id; chay lai khong doi gi.
//
//   Chay:
//     tools\mongo-repair-seller-snapshot.bat                 -> DRY RUN (chi in)
//     set APPLY=1 && tools\mongo-repair-seller-snapshot.bat  -> update that
// ============================================================================

const APPLY = process.env.APPLY === "1";
const COLL = db.seller_shops;
const MISSING_SNAPSHOT_ID = { "seller.id": { $exists: false } };

// 1. Va duoc: co sellerId top-level + co snapshot seller nhung thieu seller.id
const FIXABLE = {
  $and: [MISSING_SNAPSHOT_ID, { sellerId: { $ne: null } }, { seller: { $type: "object" } }]
};
// 2. Khong va duoc: thieu ca sellerId -> phai bo sung thu cong
const UNFIXABLE = {
  $and: [MISSING_SNAPSHOT_ID, { $or: [{ sellerId: null }, { sellerId: { $exists: false } }] }]
};
// 3. Thieu han snapshot seller nhung co sellerId -> service tu backfill khi duyet
const NO_SNAPSHOT = {
  $and: [{ sellerId: { $ne: null } }, { $or: [{ seller: { $exists: false } }, { seller: null }] }]
};

const fixableCount = COLL.countDocuments(FIXABLE);
const unfixableCount = COLL.countDocuments(UNFIXABLE);
const noSnapshotCount = COLL.countDocuments(NO_SNAPSHOT);

print("==============================================================");
print(" 14_repair_shop_seller_snapshot.js | che do = " + (APPLY ? "APPLY (UPDATE THAT)" : "DRY RUN (khong ghi)"));
print("==============================================================");
print("seller_shops: tong = " + COLL.countDocuments({}) + " | thieu seller.id = " + COLL.countDocuments(MISSING_SNAPSHOT_ID));
print("  - va duoc (co sellerId + co snapshot)                : " + fixableCount);
print("  - thieu han snapshot seller (API backfill khi duyet) : " + noSnapshotCount);
print("  - KHONG va duoc (thieu ca sellerId)                  : " + unfixableCount);

print("");
print("Chi tiet document thieu seller.id:");
COLL.find(MISSING_SNAPSHOT_ID).forEach(function (s) {
  print("   shop _id=" + s._id + " | sellerId=" + s.sellerId + " | shopName=" + s.shopName
    + " | status=" + s.approvalStatus + " | seller.id=" + (s.seller ? s.seller.id : "<khong co seller>"));
});

if (unfixableCount > 0) {
  print("");
  print("[CANH BAO] Cac shop thieu ca sellerId - phai bo sung thu cong:");
  COLL.find(UNFIXABLE).forEach(function (s) { print("   shop _id=" + s._id + " | shopName=" + s.shopName); });
}

if (fixableCount === 0) {
  print("");
  print("Khong co gi de va - du lieu da sach (idempotent: chay lai khong thay doi).");
} else if (!APPLY) {
  print("");
  print("DRY RUN - chua ghi gi. Chay lai voi APPLY=1 de update that.");
} else {
  const res = COLL.updateMany(FIXABLE, [{ $set: { "seller.id": "$sellerId", updatedAt: new Date() } }]);
  print("");
  print("--- DA UPDATE ---");
  print("   matched=" + res.matchedCount + " | modified=" + res.modifiedCount);
  print("   con lai thieu seller.id = " + COLL.countDocuments(MISSING_SNAPSHOT_ID));
}

print("");
print("Kiem tra lai: db.seller_shops.countDocuments({'seller.id':{$exists:false}}) -> mong doi 0");
