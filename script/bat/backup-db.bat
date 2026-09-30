@echo off
title USMIND - Backup Database
echo ========================================
echo   USMIND Database Backup (mysqldump)
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "MYSQL_BIN=%ROOT%\tools\mysql-8.0.36-winx64\bin"
set "BACKUP_DIR=%ROOT%\backup\db"
set "KEEP_DAYS=7"

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

for /f "tokens=1-3 delims=/ " %%a in ('echo %date%') do set "D=%%a"
for /f "tokens=1-3 delims=: " %%a in ('echo %time%') do set "T=%%a%%b%%c"
set "STAMP=%D%_%T%"
set "STAMP=%STAMP: =0%"
set "STAMP=%STAMP:-=%"
set "STAMP=%STAMP:%%=%"
set "STAMP=%STAMP:~0,15%"

echo [Backup] dumping usmind_db...
"%MYSQL_BIN%\mysqldump.exe" -uroot -proot --single-transaction --routines --triggers --databases usmind_db > "%BACKUP_DIR%\usmind_db_%STAMP%.sql" 2>nul

if %errorlevel% neq 0 (
    echo [Error] backup failed
    pause
    exit /b 1
)

echo [OK] %BACKUP_DIR%\usmind_db_%STAMP%.sql

echo [Clean] removing backups older than %KEEP_DAYS% days...
forfiles /p "%BACKUP_DIR%" /m usmind_db_*.sql /d -%KEEP_DAYS% /c "cmd /c del @path" 2>nul

echo.
echo [Done] Backup finished
pause
