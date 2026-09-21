@echo off
REM ==========================================================================
REM _mongo-env.bat - Bien moi truong dung chung cho toan bo script MongoDB.
REM Duoc "call" tu cac script khac: call "%~dp0_mongo-env.bat"
REM Sua cac duong dan duoi day neu ban cai dat o cho khac.
REM ==========================================================================

REM --- MongoDB Server (mongod.exe) -----------------------------------------
set "MONGO_SERVER=C:\Program Files\MongoDB\Server\8.3"

REM --- MongoDB Shell (mongosh) --------------------------------------------
set "MONGOSH=%LOCALAPPDATA%\Programs\mongosh\mongosh.exe"
if not exist "%MONGOSH%" (
  for /f "delims=" %%i in ('where mongosh 2^>nul') do set "MONGOSH=%%i"
)

REM --- MongoDB Database Tools (mongodump/mongorestore/mongoimport/mongoexport)
REM     Cai bang: winget install --id MongoDB.DatabaseTools -e
set "MONGO_TOOLS="
if exist "C:\Program Files\MongoDB\Tools\100\bin\mongodump.exe" set "MONGO_TOOLS=C:\Program Files\MongoDB\Tools\100\bin"
if exist "C:\Program Files\MongoDB\Tools\101\bin\mongodump.exe" set "MONGO_TOOLS=C:\Program Files\MongoDB\Tools\101\bin"
if exist "%LOCALAPPDATA%\Programs\MongoDB Tools\100\bin\mongodump.exe" set "MONGO_TOOLS=%LOCALAPPDATA%\Programs\MongoDB Tools\100\bin"
if defined MONGO_TOOLS set "PATH=%MONGO_TOOLS%;%PATH%"

REM --- MongoDB Compass (GUI tool - tieu chi 3) -----------------------------
set "COMPASS=%LOCALAPPDATA%\MongoDBCompass\MongoDBCompass.exe"
if not exist "%COMPASS%" set "COMPASS=C:\Program Files\MongoDB Compass\MongoDBCompass.exe"

REM --- Instance rieng cua do an (replica set single-node) ------------------
set "PORT=27018"
set "RS_NAME=rs0"
set "DB_NAME=bookom"
set "DB_PATH=D:\mongo-data\rs0"
set "MONGO_URI=mongodb://127.0.0.1:%PORT%/%DB_NAME%?replicaSet=%RS_NAME%"

REM --- Thu muc lam viec cua do an ------------------------------------------
set "PROJECT_DIR=%~dp0.."
set "DB_SCRIPTS=%PROJECT_DIR%\db\mongo"
set "BACKUP_DIR=%PROJECT_DIR%\backups"
set "SEED_DIR=%PROJECT_DIR%\db\seed"
