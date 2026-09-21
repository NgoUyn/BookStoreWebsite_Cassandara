@echo off
REM ==========================================================================
REM mongo-import-books.bat
REM   1) mongoimport Books.csv  ->  collection tam `books_raw`
REM   2) db\mongo\12_import_books_csv.js : chuyen doi -> `books` (schema ung dung)
REM      + gan nha ban (100 seller that) + cap nhat counters/shop + drop raw
REM Yeu cau: MongoDB 27018 dang chay; da chay tools\mongo-import-sellers.bat
REM ==========================================================================
setlocal
cd /d "%~dp0.."
call "%~dp0_mongo-env.bat"

set "CSV=%CD%\src\main\resources\Books.csv"
if not exist "%CSV%" (
  echo [LOI] Khong thay "%CSV%"
  exit /b 1
)

echo ============================================================
echo BOOKOM - Nhap sach tu Books.csv
echo   URI: %MONGO_URI%
echo   CSV: %CSV%
echo ============================================================
echo.

echo [1/2] mongoimport -> books_raw ...
"%MONGO_TOOLS%\mongoimport.exe" --uri="%MONGO_URI%" --collection=books_raw --type=csv ^
  --columnsHaveTypes ^
  --fields="ISBN.string(),Book-Title.string(),Book-Author.string(),Year-Of-Publication.string(),Publisher.string(),Image-URL-S.string(),Image-URL-M.string(),Image-URL-L.string()" ^
  --file="%CSV%" --numInsertionWorkers=4
if errorlevel 1 (
  echo [LOI] mongoimport that bai
  exit /b 1
)

echo.
echo [2/2] Chuyen doi sang collection books ...
"%MONGOSH%" "%MONGO_URI%" --file "%CD%\db\mongo\12_import_books_csv.js"

endlocal
