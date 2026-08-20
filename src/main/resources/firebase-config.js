

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.13.0/firebase-app.js";
import { getAnalytics } from "https://www.gstatic.com/firebasejs/12.13.0/firebase-analytics.js";
import { getAuth, signInWithPopup, GoogleAuthProvider } from "https://www.gstatic.com/firebasejs/12.13.0/firebase-auth.js";
const firebaseConfig = {
  apiKey: "AIzaSyDx2s2jEn3k2QBEuHlHvko6DDuU8LVtvrM",
  authDomain: "bookstore-web-188cb.firebaseapp.com",
  databaseURL: "https://bookstore-web-188cb-default-rtdb.firebaseio.com",
  projectId: "bookstore-web-188cb",
  storageBucket: "bookstore-web-188cb.firebasestorage.app",
  messagingSenderId: "87727290129",
  appId: "1:87727290129:web:e61d9e9d084720eeed3eb0",
  measurementId: "G-V31R25W302"
};

const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
const auth = getAuth(app);

// export async function loginWithGoogle() {
//   const provider = new GoogleAuthProvider();
//   const result = await signInWithPopup(auth, provider);
//   return await result.user.getIdToken();
// }

export { app, analytics, firebaseConfig };
