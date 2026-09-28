@echo off
title USMIND - Build Web Admin
echo ========================================
echo   Build admin site and deploy to release\admin
echo   (relative paths)
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "NPM=%ROOT%\tools\nodejs\npm.cmd"
set "PROJECT_DIR=%ROOT%\code\web-admin"
set "RELEASE_DIR=%ROOT%\release\admin"
set "PATH=%ROOT%\tools\nodejs;%PATH%"

cd /d "%PROJECT_DIR%"

if not exist "node_modules" (
    echo [Install] npm install...
    call "%NPM%" install
)

echo [Build] Vite build...
call "%NPM%" run build

if %errorlevel% neq 0 (
    echo [Error] Build failed
    exit /b 1
)

echo [Deploy] Copy to release\admin ...
if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%"
xcopy /e /i /y "%PROJECT_DIR%\dist\*" "%RELEASE_DIR%\" >nul

echo [Done] Admin site deployed
echo.
ping -n 3 127.0.0.1 >nul
