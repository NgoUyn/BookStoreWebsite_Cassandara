@echo off
REM ==========================================================================
REM mongo-restore.bat - KHOI PHUC tu ban sao luu (mongorestore)
REM Cach dung:  tools\mongo-restore.bat backups\bookom_..._....archive
REM             tools\mongo-restore.bat backups\bookom_....archive --drop
REM MAC DINH: KHONG --drop (restore chong len du lieu hien co).
REM           Them --drop neu muon XOA SACH roi restore lai tu dau.
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

if "%~1"=="" (
  echo !! Thieu tham so. Cach dung: tools\mongo-restore.bat ^<file .archive^> [--drop]
  if exist "%BACKUP_DIR%" (
    echo Cac ban backup dang co:
    dir /b "%BACKUP_DIR%"
  )
  goto :end
)

set "FILE=%~1"
set "EXTRA=%~2"

where mongorestore >nul 2>&1
if errorlevel 1 (
  echo !! Khong tim thay mongorestore.exe ^(MongoDB Database Tools^).
  echo    Cai bang:  winget install --id MongoDB.DatabaseTools -e
  goto :end
)

echo CANH BAO: ban sap restore tu %FILE% vao database %DB_NAME%
echo           neu file co san phan --drop thi du lieu hien tai SE BI XOA.
pause

echo Dang restore ...
mongorestore --uri="%MONGO_URI%" --archive="%FILE%" --gzip %EXTRA%

echo.
echo Kiem chung so luong document sau khi restore:
"%MONGOSH%" "%MONGO_URI%" --quiet --eval "db.getCollectionNames().sort().forEach(function(c){ if(c.indexOf('system.')!==0) print('  '+c+': '+db.getCollection(c).countDocuments({})) })"

:end
endlocal
