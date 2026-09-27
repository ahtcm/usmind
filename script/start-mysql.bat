@echo off
title USMIND - MySQL
echo ========================================
echo   Start MySQL 8.0
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."
set "ROOT=%CD%"
set "MYSQL_DIR=%ROOT%\tools\mysql-8.0.36-winx64"
set "MYSQL_DATA=%MYSQL_DIR%\data"

if not exist "%MYSQL_DATA%" (
    echo [Init] First run, initializing database...
    "%MYSQL_DIR%\bin\mysqld.exe" --initialize-insecure --console --basedir="%MYSQL_DIR%" --datadir="%MYSQL_DATA%"
    echo [Init] Done, root password empty
)

cd /d "%MYSQL_DIR%"
echo [Start] MySQL...
start "USMIND-MySQL" "bin\mysqld.exe" --console --basedir="%MYSQL_DIR%" --datadir="%MYSQL_DATA%" --lc-messages-dir="%MYSQL_DIR%\share"

ping -n 4 127.0.0.1 >nul
echo [Done] MySQL started (port 3306)
echo.