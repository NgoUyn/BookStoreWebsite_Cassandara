@echo off
REM ==========================================================================
REM mongo-shell.bat - Mo mongosh ket noi vao DB cua do an
REM ==========================================================================
setlocal
call "%~dp0_mongo-env.bat"
echo Ket noi: %MONGO_URI%
"%MONGOSH%" "%MONGO_URI%"
endlocal
