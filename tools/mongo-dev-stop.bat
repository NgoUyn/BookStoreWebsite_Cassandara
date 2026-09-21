@echo off
REM ==========================================================================
REM mongo-dev-stop.bat - Dung MongoDB dev instance (cong 27018)
REM LUU Y: KHONG dung service "MongoDB" cua Windows (cong 27017).
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo Dang dung mongod tren cong %PORT% ...
"%MONGOSH%" --port %PORT% --quiet --eval "db.getSiblingDB('admin').shutdownServer()"
ping -n 3 127.0.0.1 >nul

echo Kiem tra lai cong %PORT% ^(khong con dong nao = da dung^):
netstat -ano | findstr ":%PORT% " | findstr LISTENING

endlocal
