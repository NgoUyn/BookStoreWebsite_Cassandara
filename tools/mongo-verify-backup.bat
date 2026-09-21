@echo off
REM ==========================================================================
REM mongo-verify-backup.bat - KIEM CHUNG QUY TRINH BACKUP + RESTORE
REM   1) mongodump  -> file .archive trong backups\
REM   2) mongorestore vao database TEST (khong dung du lieu goc)
REM   3) So sanh so document tung collection -> ket luan THANH CONG / LECH
REM Dung de chup anh "bien ban restore thanh cong" cho bao cao.
REM Database test giu lai de xem trong Compass: bookom_restoretest
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"
set "TEST_DB=bookom_restoretest"

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"
set "FILE=%BACKUP_DIR%\verify_restore.archive"

echo [1/3] mongodump database %DB_NAME% ...
mongodump --uri="%MONGO_URI%" --archive="%FILE%" --gzip

echo.
echo [2/3] Xoa DB test cu (neu co) va mongorestore vao %TEST_DB% ...
"%MONGOSH%" "%MONGO_URI%" --quiet --eval "db.getSiblingDB('%TEST_DB%').dropDatabase()"
mongorestore --uri="%MONGO_URI%" --archive="%FILE%" --gzip --nsFrom="%DB_NAME%.*" --nsTo="%TEST_DB%.*"

echo.
echo [3/3] Doi chieu so document ...
"%MONGOSH%" "%MONGO_URI%" --quiet --file "%DB_SCRIPTS%\07_verify_restore.js"

echo.
echo File backup dung de kiem chung: %FILE%
echo DB sau restore (xem trong Compass): %TEST_DB%
echo Don DB test khi khong can nua:
echo   %MONGOSH% "%MONGO_URI%" --eval "db.getSiblingDB('%TEST_DB%').dropDatabase()"
endlocal
