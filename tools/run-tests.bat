@echo off
REM ============================================================
REM  run-tests.bat - Chay test suite (Mockito + @DataMongoTest)
REM  Yeu cau: MongoDB dev instance o port 27018 (tools\mongo-dev-start.bat)
REM  Log: logs\test-bg.log  -> tim dong "Tests run:" va "BUILD SUCCESS"
REM ============================================================
cd /d "%~dp0.."
echo [run-tests] Thu muc lam viec: %CD%
call "%~dp0..\mvnw.cmd" -o test > "%~dp0..\logs\test-bg.log" 2>&1
echo [run-tests] Ket qua:
findstr /r "ests.run BUILD" "%~dp0..\logs\test-bg.log"
