@echo off
REM ==========================================================================
REM mongo-demo-accounts.bat - Tao TAI KHOAN DEMO (role BUYER) de dang nhap/demo
REM
REM   buyer@gmail.com / Buyer123@                 <- tai khoan demo chinh
REM   buyer01..buyer20@gmail.com / Buyer123@      <- de sinh du lieu giao dich
REM   (ADMIN:  admin@gmail.com / Admin123@
REM    SELLER: nhaxuatbantre@bookom.vn / Nhaxuatbantre123@  ... xem 11_sellers_data.js)
REM
REM   Vi sao phai qua API: mongosh khong bam duoc bcrypt (thieu jbcrypt) nen
REM   phai de app bam ho:  /api/auth/otp/request -> /otp/verify -> /register
REM
REM   Yeu cau: MongoDB 27018 (mongo-dev-start.bat) + APP dang chay (run-app.bat)
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Tao tai khoan demo qua API
echo   App: http://localhost:8080
echo ============================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0seed-demo-accounts.ps1"

endlocal
