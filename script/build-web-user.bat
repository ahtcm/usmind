@echo off
title USMIND - Build Web User
echo ========================================
echo   Build user site and deploy to release\usmind
echo ========================================
echo.

set NPM=C:\Users\25750\Desktop\usmind\tools\nodejs\npm.cmd
set PROJECT_DIR=C:\Users\25750\Desktop\usmind\code\web-user
set RELEASE_DIR=C:\Users\25750\Desktop\usmind\release\usmind

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

echo [Deploy] Copy to release\usmind ...
if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%"
xcopy /e /i /y "%PROJECT_DIR%\dist\*" "%RELEASE_DIR%\" >nul

echo [Done] User site deployed
echo.
