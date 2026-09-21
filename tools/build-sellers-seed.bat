@echo off
REM ==========================================================================
REM build-sellers-seed.bat
REM   db\seed\sellers_real.txt  ->  db\mongo\11_sellers_data.js
REM   (sinh email/slug + HASH BCRYPT THAT cost 12 cho tung nha ban)
REM Chi can chay lai khi ban sua danh sach ten nha ban.
REM ==========================================================================
setlocal
cd /d "%~dp0.."

set "JBCRYPT=%USERPROFILE%\.m2\repository\org\mindrot\jbcrypt\0.4\jbcrypt-0.4.jar"
if not exist "%JBCRYPT%" (
  echo [LOI] Khong tim thay jbcrypt: "%JBCRYPT%"
  echo       Chay: .\mvnw.cmd -o dependency:get -Dartifact=org.mindrot:jbcrypt:0.4
  exit /b 1
)

if not exist "%TEMP%\bcrypt-tmp" mkdir "%TEMP%\bcrypt-tmp"

echo [1/2] Bien dich tools\BuildSellersSeed.java ...
javac -encoding UTF-8 -cp "%JBCRYPT%" -d "%TEMP%\bcrypt-tmp" tools\BuildSellersSeed.java
if errorlevel 1 exit /b 1

echo [2/2] Sinh db\mongo\11_sellers_data.js ...
java -cp "%JBCRYPT%;%TEMP%\bcrypt-tmp" BuildSellersSeed db\seed\sellers_real.txt db\mongo\11_sellers_data.js
if errorlevel 1 exit /b 1

echo.
echo Xong. Buoc tiep theo: tools\mongo-import-sellers.bat
endlocal
