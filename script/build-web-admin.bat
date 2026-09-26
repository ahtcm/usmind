@echo off
title USMIND - Build Web Admin
echo ========================================
echo   Build admin site and deploy to release\admin
echo ========================================
echo.

set NPM=C:\Users\25750\Desktop\usmind\tools\nodejs\npm.cmd
set PROJECT_DIR=C:\Users\25750\Desktop\usmind\code\web-admin
set RELEASE_DIR=C:\Users\25750\Desktop\usmind\release\admin

cd /d "%PROJECT_DIR%"

if not exist "node_modules" (
    echo [Install] npm install...
    call %NPM% install
)

echo [Build] Vite build...
call %NPM% run build

if %errorlevel% neq 0 (
    echo [Error] Build failed
    exit /b 1
)

echo [Deploy] Copy to release\admin ...
if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%"
xcopy /e /i /y "%PROJECT_DIR%\dist\*" "%RELEASE_DIR%\" >nul

echo [Done] Admin site deployed
echo.
