@echo off
REM ==========================================================================
REM mongo-dev-start.bat - Khoi dong MongoDB (replica set single-node) cua do an
REM Cong: 27018 | DB: bookom | Data: D:\mongo-data\rs0
REM Idempotent: chay lai khi dang chay cung khong loi.
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Khoi dong MongoDB dev instance
echo   mongod   : %MONGO_SERVER%\bin\mongod.exe
echo   dbpath   : %DB_PATH%
echo   port     : %PORT%   (replica set: %RS_NAME%)
echo ============================================================

if not exist "%MONGO_SERVER%\bin\mongod.exe" (
  echo !! Khong tim thay mongod.exe tai %MONGO_SERVER%\bin
  echo !! Sua bien MONGO_SERVER trong tools\_mongo-env.bat
  goto :end
)

echo [1/4] Kiem tra cong %PORT% ...
netstat -ano | findstr ":%PORT% " | findstr LISTENING >nul
if %errorlevel%==0 (
  echo        - Da co mongod dang lang nghe tren cong %PORT% ^(bo qua khoi dong^)
) else (
  echo        - Dang khoi dong mongod ...
  if not exist "%DB_PATH%" mkdir "%DB_PATH%"
  start "bookom-mongod" /min "%MONGO_SERVER%\bin\mongod.exe" --dbpath "%DB_PATH%" --port %PORT% --replSet %RS_NAME% --bind_ip 127.0.0.1 --logpath "%DB_PATH%\mongod.log" --logappend
  ping -n 7 127.0.0.1 >nul
)

echo [2/4] Khoi tao replica set ^(idempotent^) ...
"%MONGOSH%" --port %PORT% --file "%DB_SCRIPTS%\00_init_replica_set.js"

echo [3/4] Kiem tra ket noi ...
"%MONGOSH%" --port %PORT% --quiet --eval "print(db.getSiblingDB('bookom').runCommand({ping:1}).ok ? 'PING OK' : 'PING FAILED')"

echo [4/4] URI cho Spring Boot:
echo        %MONGO_URI%
echo        Log mongod: %DB_PATH%\mongod.log

:end
endlocal
