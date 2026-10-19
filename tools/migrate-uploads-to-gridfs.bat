@echo off
REM ==========================================================================
REM migrate-uploads-to-gridfs.bat
REM   Chuyen anh dang nam tren dia (uploads/...) vao GridFS cua MongoDB.
REM   Sau khi chay, `tools\mongo-backup.bat` (mongodump --gzip --archive)
REM   sao luu duoc CA DU LIEU LAN ANH trong 1 file.
REM   Script: db\mongo\15_migrate_uploads_to_gridfs.js (idempotent)
REM Cach dung:
REM   tools\migrate-uploads-to-gridfs.bat                 -> DRY RUN (chi in)
REM   set APPLY=1 && tools\migrate-uploads-to-gridfs.bat  -> ghi that vao GridFS
REM Yeu cau: MongoDB 27018 dang chay (tools\mongo-dev-start.bat).
REM ==========================================================================
setlocal
cd /d "%~dp0.."
call "%~dp0_mongo-env.bat"

set "UPLOAD_ROOT=%CD%"

echo ============================================================
echo BOOKOM - Migrate anh uploads/ -^> GridFS   APPLY=%APPLY%
echo   URI    : %MONGO_URI%
echo   Root   : %UPLOAD_ROOT%
echo   Script : db\mongo\15_migrate_uploads_to_gridfs.js
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --file "%DB_SCRIPTS%\15_migrate_uploads_to_gridfs.js"

echo.
echo (Nhac lai) Muon ghi that: set APPLY=1 ^&^& tools\migrate-uploads-to-gridfs.bat
endlocal
