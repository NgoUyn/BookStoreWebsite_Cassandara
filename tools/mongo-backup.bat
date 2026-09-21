@echo off
REM ==========================================================================
REM mongo-backup.bat - SAO LUU toan bo database (mongodump, nen --gzip --archive)
REM Ket qua: backups\bookom_<ngay>_<gio>.archive
REM Khoi phuc bang:  tools\mongo-restore.bat <ten-file>
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

where mongodump >nul 2>&1
if errorlevel 1 (
  echo !! Khong tim thay mongodump.exe ^(MongoDB Database Tools^).
  echo    Cai bang:  winget install --id MongoDB.DatabaseTools -e
  echo    Hoac chay bang Docker:
  echo      docker run --rm -v "%BACKUP_DIR%:/backup" mongodb/mongodb-database-tools ^
  echo        mongodump --uri="mongodb://host.docker.internal:%PORT%/%DB_NAME%?replicaSet=%RS_NAME%" ^
  echo        --archive=/backup/bookom.archive --gzip
  goto :end
)

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"
for /f "tokens=1-4 delims=/ " %%a in ("%date%") do set "D=%%d%%b%%c"
set "T=%time::=%"
set "T=%T: =0%"
set "FILE=%BACKUP_DIR%\%DB_NAME%_%D%_%T:~0,4%.archive"

echo Dang sao luu database %DB_NAME% ...
mongodump --uri="%MONGO_URI%" --archive="%FILE%" --gzip

echo.
echo Hoan tat. File backup: %FILE%
dir /b "%BACKUP_DIR%"

:end
endlocal
