@echo off
REM ============================================================
REM  run-app.bat - Chay ung dung Spring Boot tren MongoDB
REM  Yeu cau: MongoDB 27018 dang chay (tools\mongo-dev-start.bat)
REM  Log: logs\app-run.log   | Dung: Ctrl+C hoac tools\stop-app.bat
REM ============================================================
cd /d "%~dp0.."
echo [run-app] MongoDB URI: mongodb://127.0.0.1:27018/bookom?replicaSet=rs0
echo [run-app] Log: logs\app-run.log
call "%~dp0..\mvnw.cmd" -o spring-boot:run > "%~dp0..\logs\app-run.log" 2>&1
