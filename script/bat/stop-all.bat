@echo off
title USMIND - Stop All Services
echo ========================================
echo   Stop USMIND All Services
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"
set "MYSQL_DIR=%ROOT%\tools\mysql-8.0.36-winx64"

echo [1/5] Stopping Nginx (8866/8867/8766)...
taskkill /f /im nginx.exe 2>nul

echo [2/5] Stopping Backend Java (port 8899)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8899" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a 2>nul
)

echo [3/5] Stopping Redis (6379)...
taskkill /f /im redis-server.exe 2>nul

echo [4/5] Stopping MySQL (graceful)...
"%MYSQL_DIR%\bin\mysqladmin.exe" -uroot -proot shutdown 2>nul
if errorlevel 1 taskkill /f /im mysqld.exe 2>nul

echo [5/5] Cleaning Node leftovers (Vite dev)...
taskkill /f /im node.exe 2>nul

echo.
echo [DONE] All USMIND services stopped
echo        (Nginx is shared; restart it if motorlab also uses it)
echo.
pause