@echo off
REM ==========================================================================
REM mongo-rebuild-data.bat
REM  - BUOC 1 cua "xay lai du lieu that": xoa toan bo du lieu bia
REM  - mac dinh CHI IN RA (dry run). Muon xoa that:  set APPLY=1
REM Yeu cau: MongoDB 27018 dang chay; NEN TAT app truoc khi chay.
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"

echo ============================================================
echo BOOKOM - Xoa du lieu bia
echo   URI  : %MONGO_URI%
echo   Mode : %APPLY%  (dat "set APPLY=1" truoc khi chay de xoa that)
echo ============================================================
echo.

"%MONGOSH%" "%MONGO_URI%" --file "%DB_SCRIPTS%\10_purge_fake_data.js"

endlocal
