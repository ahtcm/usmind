@echo off
title USMIND - Nginx
echo ========================================
echo   Start Nginx
echo ========================================
echo.

set NGINX_DIR=C:\Users\25750\Desktop\usmind\tools\nginx-1.30.5

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

timeout /t 2 /nobreak >nul
echo [Done] Nginx started
echo   - User site:  http://127.0.0.1:8866
echo   - Admin site: http://127.0.0.1:8867
echo.
