// ============================================================================
// 07_verify_restore.js - So sanh so document giua DB goc va DB sau khi restore
//   Chay tu tools\mongo-verify-backup.bat (khong chay truc tiep)
//   Bien moi truong: DB_NAME (goc), TEST_DB (sau restore)
// ============================================================================

const src = db.getSiblingDB(process.env.DB_NAME || "bookom");
const dst = db.getSiblingDB(process.env.TEST_DB || "bookom_restoretest");

print("So sanh: " + src.getName() + "  (goc)   <->   " + dst.getName() + "  (sau restore)");
print("---------------------------------------------------------------------");

let ok = true, checked = 0;
const cols = src.getCollectionNames().filter(c => c.indexOf("system.") !== 0).sort();
cols.forEach(function (c) {
  const a = src.getCollection(c).countDocuments({});
  const b = dst.getCollection(c).countDocuments({});
  const mark = (a === b) ? "OK" : "LECH";
  if (a !== b) { ok = false; }
  checked++;
  print("  " + (c + "                              ").substring(0, 28) +
    " nguon=" + (a + "     ").substring(0, 6) + " | restore=" + (b + "     ").substring(0, 6) + " [" + mark + "]");
});

print("---------------------------------------------------------------------");
print("Da doi chieu " + checked + " collection.");
print(ok
  ? "==> KET QUA: RESTORE THANH CONG - so document khop 100%"
  : "==> KET QUA: CO LECH so document - kiem tra lai ban backup");
