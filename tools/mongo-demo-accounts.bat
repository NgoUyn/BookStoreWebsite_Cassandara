@echo off
REM ==========================================================================
REM mongo-demo-accounts.bat
REM Tao 2 tai khoan THAT dang nhap duoc (hash bcrypt that) + shop + gan sach:
REM   SELLER : shop_nha_nam@gmail.com / Nhanam123@  (shop "Nha Nam Official")
REM   ADMIN  : admin@gmail.com        / Admin123@
REM Yeu cau: MongoDB 27018 dang chay (tools\mongo-dev-start.bat)
REM Idempotent: chay lai nhieu lan khong tao trung.
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Tao tai khoan demo (SELLER + ADMIN)
echo   URI: %MONGO_URI%
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --file "%DB_SCRIPTS%\09_demo_accounts.js"

echo.
echo Neu dang nhap van that bai, kiem tra:
echo   1) App dang chay dung MongoDB 27018/bookom (GET /api/health/detailed)
echo   2) Ro leo lai app de nap code moi: tools\run-app.bat
endlocal
