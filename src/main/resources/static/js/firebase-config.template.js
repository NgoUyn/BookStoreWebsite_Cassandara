/**
 * Firebase Configuration - TEMPLATE
 * 
 * Hướng dẫn:
 * 1. Copy file này thành firebase-config.js
 * 2. Điền thông tin Firebase Web App của bạn từ Firebase Console
 * 3. KHÔNG commit firebase-config.js lên GitHub (đã được .gitignore)
 * 
 * Firebase Console: https://console.firebase.google.com
 * Project settings → General → Your apps → Web app
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.13.0/firebase-app.js";
import { getAnalytics } from "https://www.gstatic.com/firebasejs/12.13.0/firebase-analytics.js";

const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_AUTH_DOMAIN",
  databaseURL: "YOUR_DATABASE_URL",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_STORAGE_BUCKET",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID",
  measurementId: "YOUR_MEASUREMENT_ID"
};

const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);

export { app, analytics, firebaseConfig };
