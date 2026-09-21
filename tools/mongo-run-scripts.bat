@echo off
REM ==========================================================================
REM mongo-run-scripts.bat
REM Chay lan luot: 00 (replica set) -> 01 -> 02 -> 03 -> 04 -> 05
REM Cach dung:
REM   tools\mongo-run-scripts.bat            (chay tat ca)
REM   tools\mongo-run-scripts.bat 01 02      (chi chay script duoc liet ke)
REM ==========================================================================
setlocal enabledelayedexpansion
call "%~dp0_mongo-env.bat"

set "SCRIPTS=%*"
if "%SCRIPTS%"=="" set "SCRIPTS=00 01 02 03 04 05"

echo ============================================================
echo BOOKOM - Chay script MongoDB
echo   URI: %MONGO_URI%
echo   Bo script: %SCRIPTS%
echo ============================================================

for %%N in (%SCRIPTS%) do (
  if "%%N"=="00" set "FILE=00_init_replica_set.js"
  if "%%N"=="01" set "FILE=01_create_collections_validators.js"
  if "%%N"=="02" set "FILE=02_indexes.js"
  if "%%N"=="03" set "FILE=03_seed_reference.js"
  if "%%N"=="04" set "FILE=04_queries_basic.js"
  if "%%N"=="05" set "FILE=05_queries_advanced.js"
  echo.
  echo ------------------------------------------------------------
  echo [RUN] !FILE!
  echo ------------------------------------------------------------
  "%MONGOSH%" "%MONGO_URI%" --file "%DB_SCRIPTS%\!FILE!"
)

echo.
echo ============================================================
echo Xong. Log co the luu lai bang:  ... ^> logs\mongo-run.log 2^>^&1
echo ============================================================
endlocal
