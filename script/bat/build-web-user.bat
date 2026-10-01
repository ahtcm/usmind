@echo off
title USMIND - Build Web User
echo ========================================
echo   Build user site and deploy to release\usmind
echo   (relative paths)
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "NPM=%ROOT%\tools\nodejs\npm.cmd"
set "PROJECT_DIR=%ROOT%\code\web-user"
set "RELEASE_DIR=%ROOT%\release\usmind"
set "PATH=%ROOT%\tools\nodejs;%PATH%"

cd /d "%PROJECT_DIR%"

if not exist "node_modules" (
    echo [Install] npm install...
    call "%NPM%" install
)

echo [Patch] Apply simple-mind-map patches (idempotent)...
python "%ROOT%\script\python\patch_root_expand.py"
if %errorlevel% neq 0 (
    echo [Error] Patch failed
    exit /b 1
)

echo [Build] Vite build...
call "%NPM%" run build

if %errorlevel% neq 0 (
    echo [Error] Build failed
    exit /b 1
)

echo [Deploy] Copy to release\usmind ...
if exist "%RELEASE_DIR%" rmdir /s /q "%RELEASE_DIR%"
xcopy /e /i /y "%PROJECT_DIR%\dist\*" "%RELEASE_DIR%\" >nul

echo [Done] User site deployed
echo.
ping -n 3 127.0.0.1 >nul
