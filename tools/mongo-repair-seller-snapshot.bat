@echo off
REM ==========================================================================
REM mongo-repair-seller-snapshot.bat
REM   Va du lieu legacy: shop thieu `seller.id` trong snapshot nguoi ban.
REM   Nguyen nhan gay HTTP 500 khi admin duyet ho so:
REM     PUT /api/admin/seller-shops-management/{id}/approve
REM   Script: db\mongo\14_repair_shop_seller_snapshot.js (idempotent)
REM Cach dung:
REM   tools\mongo-repair-seller-snapshot.bat                -> DRY RUN (chi in)
REM   set APPLY=1 && tools\mongo-repair-seller-snapshot.bat -> update that
REM Yeu cau: MongoDB 27018 dang chay.
REM ==========================================================================
setlocal
cd /d "%~dp0.."
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Va du lieu shop thieu seller.id   APPLY=%APPLY%
echo   URI    : %MONGO_URI%
echo   Script : db\mongo\14_repair_shop_seller_snapshot.js
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --file "%CD%\db\mongo\14_repair_shop_seller_snapshot.js"

endlocal
