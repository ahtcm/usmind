@echo off
title USMIND - One-Click Delete (Cleanup)
echo ========================================
echo   USMIND One-Click Delete - Clean Runtime
echo ========================================
echo.
echo   Will do:
echo     1. Stop all services
echo     2. Delete logs (logs/)
echo     3. Delete build outputs (backend\target, frontend dist)
echo     4. [Optional] Delete MySQL data (full reset)
echo.
echo   [NOTE] Source code, release outputs and tools are KEPT
echo.

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..
set MYSQL_DIR=%ROOT%\tools\mysql-8.0.36-winx64

rem ========== 1. Stop all services ==========
echo [1/4] Stopping all services...
taskkill /f /im nginx.exe 2>nul
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8899" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a 2>nul
)
taskkill /f /im redis-server.exe 2>nul
"%MYSQL_DIR%\bin\mysqladmin.exe" -uroot -proot shutdown 2>nul
if errorlevel 1 taskkill /f /im mysqld.exe 2>nul
taskkill /f /im node.exe 2>nul
timeout /t 2 /nobreak >nul

rem ========== 2. Clean logs ==========
echo [2/4] Cleaning logs...
if exist "%ROOT%\logs" rmdir /s /q "%ROOT%\logs"
if exist "%ROOT%\release\logs" rmdir /s /q "%ROOT%\release\logs"
if exist "%ROOT%\release\usmind\logs" rmdir /s /q "%ROOT%\release\usmind\logs"
if exist "%ROOT%\release\admin\logs" rmdir /s /q "%ROOT%\release\admin\logs"
if exist "%ROOT%\code\backend\logs" rmdir /s /q "%ROOT%\code\backend\logs"
if exist "%ROOT%\tools\nginx-1.30.5\logs" del /q "%ROOT%\tools\nginx-1.30.5\logs\*.*" 2>nul

rem ========== 3. Clean build outputs ==========
echo [3/4] Cleaning build outputs...
if exist "%ROOT%\code\backend\target" rmdir /s /q "%ROOT%\code\backend\target"
if exist "%ROOT%\code\web-user\dist" rmdir /s /q "%ROOT%\code\web-user\dist"
if exist "%ROOT%\code\web-admin\dist" rmdir /s /q "%ROOT%\code\web-admin\dist"

rem ========== 4. Optional: delete MySQL data ==========
echo.
set /p CONFIRM=  Delete MySQL data too (full reset, need init-db again)? [y/N]:
if /i "%CONFIRM%"=="y" (
    echo [Deleting] MySQL data...
    taskkill /f /im mysqld.exe 2>nul
    if exist "%MYSQL_DIR%\data" rmdir /s /q "%MYSQL_DIR%\data"
    if exist "%ROOT%\tools\Redis-x64-5.0.14.1\dump.rdb" del /q "%ROOT%\tools\Redis-x64-5.0.14.1\dump.rdb"
    echo [Deleted] MySQL + Redis data cleared
    echo           Next start: run script\init-db.bat first
) else (
    echo [Skipped] Database data kept
)

echo.
echo ========================================
echo   Cleanup done
echo   - Source / release outputs / tools KEPT
echo ========================================
echo.
pause
