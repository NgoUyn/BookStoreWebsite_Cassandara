@echo off
REM ==========================================================================
REM mongo-import.bat - IMPORT du lieu tu file JSON/CSV vao collection
REM Cach dung:
REM   tools\mongo-import.bat <collection> <file> [json|jsonArray|csv] [--drop]
REM Vi du:
REM   tools\mongo-import.bat categories db\seed\categories.json jsonArray --drop
REM   tools\mongo-import.bat books db\seed\books.csv csv
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

if "%~1"=="" (
  echo !! Cach dung: tools\mongo-import.bat ^<collection^> ^<file^> [json^|jsonArray^|csv] [--drop]
  goto :end
)
set "COLL=%~1"
set "FILE=%~2"
set "TYPE=%~3"
if "%TYPE%"=="" set "TYPE=jsonArray"
set "EXTRA=%~4"

if not exist "%FILE%" (
  echo !! Khong tim thay file: %FILE%
  goto :end
)

where mongoimport >nul 2>&1
if errorlevel 1 (
  echo !! Khong tim thay mongoimport.exe ^(MongoDB Database Tools^).
  echo    Cai bang:  winget install --id MongoDB.DatabaseTools -e
  goto :end
)

echo Import %FILE% vao collection %COLL% (type=%TYPE%) ...
if /i "%TYPE%"=="csv" (
  mongoimport --uri="%MONGO_URI%" --collection="%COLL%" --type=csv --headerline --file="%FILE%" %EXTRA%
) else (
  mongoimport --uri="%MONGO_URI%" --collection="%COLL%" --type=%TYPE% --file="%FILE%" %EXTRA%
)

echo.
echo So document hien co trong %COLL%:
"%MONGOSH%" "%MONGO_URI%" --quiet --eval "print(db.getCollection('%%COLL%%').countDocuments({}))"

:end
endlocal
