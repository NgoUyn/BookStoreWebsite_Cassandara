@echo off
REM ==========================================================================
REM _bg-run.bat - Chay 1 script khac o CHE DO NEN (khong chan terminal),
REM              ghi toan bo stdout/stderr ra file log de theo doi.
REM   Cach dung:  tools\_bg-run.bat <file-log> <script> [tham-so...]
REM   Vi du:      set APPLY=1&& tools\_bg-run.bat logs\purge.log tools\mongo-rebuild-data.bat
REM   Theo doi:   type logs\purge.log
REM   Luu y: thu muc "logs" se duoc tao tu dong.
REM ==========================================================================
if "%~1"=="" (
  echo Thieu tham so: _bg-run.bat ^<file-log^> ^<script^> [tham-so...]
  exit /b 1
)
set "LOG=%~1"
shift
set "CMD="
:build
if "%~1"=="" goto run
set "CMD=%CMD% %~1"
shift
goto build
:run
if not exist "%~dp0..\logs" mkdir "%~dp0..\logs"
echo [bg] log  : %LOG%
echo [bg] lenh :%CMD%
start "bookom-bg" cmd /c "%CMD% 1>%LOG% 2>&1"
