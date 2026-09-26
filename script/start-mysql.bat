@echo off
title USMIND - MySQL
echo ========================================
echo   Start MySQL 8.0
echo ========================================
echo.

set MYSQL_DIR=C:\Users\25750\Desktop\usmind\tools\mysql-8.0.36-winx64
set MYSQL_DATA=%MYSQL_DIR%\data

if not exist "%MYSQL_DATA%" (
    echo [Init] First run, initializing database...
    "%MYSQL_DIR%\bin\mysqld.exe" --initialize-insecure --console
    echo [Init] Done, root password empty
)

echo [Start] MySQL...
start "USMIND-MySQL" "%MYSQL_DIR%\bin\mysqld.exe" --console

timeout /t 3 /nobreak >nul
echo [Done] MySQL started (port 3306)
echo.
