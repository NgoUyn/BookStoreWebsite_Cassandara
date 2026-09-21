@echo off
REM ==========================================================================
REM mongo-import-sellers.bat
REM   Tao/cap nhat 100 NHA BAN THAT (user SELLER + shop APPROVED) trong MongoDB
REM   Du lieu: db\mongo\11_sellers_data.js (sinh bang tools\build-sellers-seed.bat)
REM Yeu cau: MongoDB 27018 dang chay. Idempotent (chay lai khong tao trung).
REM ==========================================================================
setlocal
cd /d "%~dp0.."
call "%~dp0_mongo-env.bat"

set "SELLERS_DATA=%CD%\db\mongo\11_sellers_data.js"
if not exist "%SELLERS_DATA%" (
  echo [LOI] Chua co "%SELLERS_DATA%"
  echo       Chay truoc: tools\build-sellers-seed.bat
  exit /b 1
)

echo ============================================================
echo BOOKOM - Import nha ban that
echo   URI : %MONGO_URI%
echo   Data: %SELLERS_DATA%
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --file "%CD%\db\mongo\11_import_sellers.js"

endlocal
