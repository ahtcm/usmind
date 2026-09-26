@echo off
chcp 65001 >nul
title USMIND - 管理后台 (Vue Dev)
echo ========================================
echo   启动 USMIND 管理后台开发服务器
echo ========================================

set NPM=C:\Users\25750\Desktop\usmind\tools\nodejs\npm.cmd
set PROJECT_DIR=C:\Users\25750\Desktop\usmind\code\web-admin

cd /d "%PROJECT_DIR%"

if not exist "node_modules" (
    echo [安装] 依赖安装中...
    call %NPM% install
)

echo [启动] Vite 开发服务器 (端口 5174)...
call %NPM% run dev

echo.
pause
