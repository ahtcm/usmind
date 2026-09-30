@echo off
title USMIND - Health Monitor (auto-restart)
echo ========================================
echo   USMIND Health Monitor
echo   Checks services every 30s and auto-restarts
echo   the ones that are down. Close this window to stop.
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "JAVA_HOME=%ROOT%\tools\jdk-17.0.2"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MONITOR_LOG=%ROOT%\logs\monitor.log"
if not exist "%ROOT%\logs" mkdir "%ROOT%\logs"

:loop
echo [%date% %time%] checking... >> "%MONITOR_LOG%"

REM ---- MySQL (3306) ----
curl -s -o nul -m 3 http://127.0.0.1:3306 2>nul
if %errorlevel% neq 0 (
    echo [%time%] MySQL DOWN, restarting >> "%MONITOR_LOG%"
    start "" /min cmd /c ""%SCRIPT_DIR%\start-mysql.bat""
)

REM ---- Redis (6379) ----
curl -s -o nul -m 3 http://127.0.0.1:6379 2>nul
if %errorlevel% neq 0 (
    echo [%time%] Redis DOWN, restarting >> "%MONITOR_LOG%"
    start "" /min cmd /c ""%SCRIPT_DIR%\start-redis.bat""
)

REM ---- Backend (8899 health) ----
curl -s -m 5 http://127.0.0.1:8899/api/health > "%TEMP%\usmind_health.txt" 2>nul
findstr /c:"200" "%TEMP%\usmind_health.txt" >nul 2>nul
if %errorlevel% neq 0 (
    echo [%time%] Backend DOWN, restarting >> "%MONITOR_LOG%"
    start "" /min cmd /c "cd /d "%ROOT%\code\backend" && java -DLOG_PATH="%ROOT%\logs" -jar "target\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\uploads""
)

REM ---- Nginx (8866) ----
curl -s -o nul -m 3 http://127.0.0.1:8866 2>nul
if %errorlevel% neq 0 (
    echo [%time%] Nginx DOWN, restarting >> "%MONITOR_LOG%"
    start "" /min cmd /c ""%SCRIPT_DIR%\start-nginx.bat""
)

timeout /t 30 /nobreak >nul
goto loop
