// ============================================================================
// 00_init_replica_set.js
// Khoi tao single-node replica set cho do an BOOKOM (Spring Boot + MongoDB).
//
// VI SAO CAN REPLICA SET?
//   - Multi-document transaction (MongoTransactionManager) CHI hoat dong tren
//     replica set (hoac sharded cluster), khong hoat dong tren standalone.
//   - Change Streams cung yeu cau replica set.
//   => Tang "ket noi CSDL" va cac tinh nang nang cao phu thuoc vao buoc nay.
//
// CACH CHAY (instance rieng cua do an, cong 27018):
//   mongosh --port 27018 --file db/mongo/00_init_replica_set.js
// hoac:
//   tools\mongo-dev-start.bat
//
// IDEMPOTENT: chay lai nhieu lan khong gay loi.
// ============================================================================

const rsName = process.env.RS_NAME || "rs0";
const rsPort = Number(process.env.RS_PORT || 27018);
const rsHost = process.env.RS_HOST || ("127.0.0.1:" + rsPort);

print("============================================================");
print("BOOKOM - Khoi tao replica set");
print("  set name : " + rsName);
print("  host     : " + rsHost);
print("============================================================");

let st = null;
try {
  st = rs.status();
} catch (e) {
  st = null; // Chua initiate -> rs.status() nem loi
}

if (st && st.ok === 1) {
  print("==> Replica set da ton tai (set: " + st.set + ", so member: " + st.members.length + ")");
} else {
  print("==> Chua co replica set -> rs.initiate(...)");
  const cfg = {
    _id: rsName,
    members: [{ _id: 0, host: rsHost, priority: 2 }]
  };
  printjson(rs.initiate(cfg));
}

// Cho instance len PRIMARY (toi da ~20s)
let waited = 0;
while (waited < 20) {
  let status = null;
  try {
    status = rs.status();
  } catch (e) {
    status = null;
  }
  if (status && status.myState === 1) {
    break;
  }
  sleep(1000);
  waited++;
}

const finalStatus = rs.status();
print("------------------------------------------------------------");
print("state      : " + (finalStatus.myState === 1 ? "PRIMARY (OK)" : "CHUA PHAI PRIMARY"));
print("members    : " + finalStatus.members.map(m => m.name + "=" + m.stateStr).join(", "));
print("------------------------------------------------------------");
if (finalStatus.myState === 1) {
  print("==> SAN SANG: transaction + change stream da dung duoc.");
} else {
  print("!! Instance khong o trang thai PRIMARY. Kiem tra: mongod co chay voi --replSet " + rsName + " ?");
}
