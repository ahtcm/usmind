@echo off
title USMIND - Nginx
echo ========================================
echo   Start Nginx
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."
set "ROOT=%CD%"
set "NGINX_DIR=%ROOT%\tools\nginx-1.30.5"

cd /d "%NGINX_DIR%"
if not exist "logs" mkdir "logs"

tasklist /fi "imagename eq nginx.exe" 2>nul | find "nginx.exe" >nul
if %errorlevel% equ 0 (
    echo [Info] Nginx running, reload...
    nginx.exe -s reload
) else (
    echo [Start] Nginx...
    start nginx.exe
)

ping -n 3 127.0.0.1 >nul
echo [Done] Nginx started
echo   - User site:  http://127.0.0.1:8866
echo   - Admin site: http://127.0.0.1:8867
echo.