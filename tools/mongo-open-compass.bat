@echo off
REM ==========================================================================
REM mongo-open-compass.bat - Mo MongoDB Compass ket noi san vao DB cua do an
REM LUU Y QUAN TRONG: do an dung cong 27018 (replica set), KHONG phai 27017
REM (27017 la MongoDB service standalone co san tren may - du lieu KHAC).
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

if not exist "%COMPASS%" (
  echo !! Khong tim thay MongoDB Compass.
  echo    Cai bang: winget install --id MongoDB.Compass.Full -e
  goto :end
)

echo Mo Compass voi URI: %MONGO_URI%
start "" "%COMPASS%" "%MONGO_URI%"

:end
endlocal
