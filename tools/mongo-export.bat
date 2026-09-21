@echo off
REM ==========================================================================
REM mongo-export.bat - EXPORT du lieu ra JSON/CSV
REM Cach dung:
REM   tools\mongo-export.bat <collection> [file] [json|csv] [query-json]
REM Vi du:
REM   tools\mongo-export.bat books exports\books.json json
REM   tools\mongo-export.bat orders exports\orders_2026.json json {"createdAt":{"$gte":{"$date":"2026-01-01T00:00:00Z"}}}
REM   tools\mongo-export.bat books exports\books.csv csv --fields=title,author,price
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

if "%~1"=="" (
  echo !! Cach dung: tools\mongo-export.bat ^<collection^> [file] [json^|csv] [query]
  goto :end
)
set "COLL=%~1"
set "TYPE=%~3"
if "%TYPE%"=="" set "TYPE=json"
set "FILE=%~2"
if "%FILE%"=="" (
  if not exist "%BACKUP_DIR%\export" mkdir "%BACKUP_DIR%\export"
  set "FILE=%BACKUP_DIR%\export\%COLL%.%TYPE%"
)
set "QUERY=%~4"

where mongoexport >nul 2>&1
if errorlevel 1 (
  echo !! Khong tim thay mongoexport.exe ^(MongoDB Database Tools^).
  echo    Cai bang:  winget install --id MongoDB.DatabaseTools -e
  goto :end
)

echo Export collection %COLL% -^> %FILE% ...
if "%QUERY%"=="" (
  mongoexport --uri="%MONGO_URI%" --collection="%COLL%" --type=%TYPE% --out="%FILE%"
) else (
  mongoexport --uri="%MONGO_URI%" --collection="%COLL%" --type=%TYPE% --query="%QUERY%" --out="%FILE%"
)

echo.
echo Hoan tat. Thong tin file xuat:
for %%F in ("%FILE%") do echo   %%~fF  ^(%%~zF bytes^)

:end
endlocal
