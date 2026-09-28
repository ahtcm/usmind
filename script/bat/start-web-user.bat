@echo off
chcp 65001 >nul
title USMIND - 用户�?(Vue Dev)
echo ========================================
echo   启动 USMIND 用户端开发服务器
echo ========================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "NPM=%ROOT%\tools\nodejs\npm.cmd"
set "PROJECT_DIR=%ROOT%\code\web-user"
set "PATH=%ROOT%\tools\nodejs;%PATH%"

cd /d "%PROJECT_DIR%"

if not exist "node_modules" (
    echo [安装] 依赖安装�?..
    call "%NPM%" install
)

echo [启动] Vite 开发服务器 (端口 5173)...
call "%NPM%" run dev

echo.
pause