@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem USMIND 一键停止脚本
rem 功能: 关闭 EXE 客户端 + 停止 usmind 的 nginx(仅 8866, 不影响其他站点)
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "NGINX=%ROOT%\tools\nginx-1.30.5"

echo.
echo ============================================
echo   USMIND 一键停止
echo ============================================
echo.

rem ---------- 1. 关闭 EXE 客户端 ----------
taskkill /IM usmind.exe /F >nul 2>&1
if errorlevel 1 ( echo [EXE] 未在运行 ) else ( echo [EXE] 已关闭 )

rem ---------- 2. 停止 nginx (仅 8866 端口) ----------
set "FOUND="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8866" ^| findstr "LISTENING"') do (
    set "FOUND=1"
    echo [nginx] 停止 PID %%P ...
    taskkill /PID %%P /F >nul 2>&1
)
if not defined FOUND echo [nginx] 未在运行

rem ---------- 清理无效 pid 文件 ----------
if exist "%NGINX%\logs\nginx.pid" del "%NGINX%\logs\nginx.pid" >nul 2>&1

echo.
echo   已全部停止
echo ============================================
exit /b 0
