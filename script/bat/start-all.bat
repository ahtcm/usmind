@echo off
title USMIND - Start All Services
echo ========================================
echo   USMIND One-Click Start
echo ========================================
echo.

set SCRIPT_DIR=%~dp0

echo [1/5] Starting MySQL...
call "%SCRIPT_DIR%start-mysql.bat"

echo [2/5] Starting Redis...
call "%SCRIPT_DIR%start-redis.bat"

echo [3/5] Starting Nginx...
call "%SCRIPT_DIR%start-nginx.bat"

echo [4/5] Starting Backend (Spring Boot, port 8899)...
start "USMIND-Backend" cmd /k "%SCRIPT_DIR%start-backend.bat"

echo [5/5] Waiting for backend, then building frontends...
echo      (First run auto npm install + build, takes 1-3 min)
timeout /t 15 /nobreak >nul

echo [Build] Web User...
call "%SCRIPT_DIR%build-web-user.bat"
echo [Build] Web Admin...
call "%SCRIPT_DIR%build-web-admin.bat"

echo.
echo ========================================
echo   ALL SERVICES STARTED
echo   - User site:   http://127.0.0.1:8866
echo   - Admin site:  http://127.0.0.1:8867
echo   - Backend API: http://127.0.0.1:8899/api
echo   - Default admin: admin / admin123
echo ========================================
echo.
pause
