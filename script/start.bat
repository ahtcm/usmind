@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem USMIND 一键启动脚本
rem 功能: 启动 nginx(8866) + 启动 EXE 客户端
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "NGINX=%ROOT%\tools\nginx-1.30.5"
set "DIST=%ROOT%\dist"

echo.
echo ============================================
echo   USMIND 一键启动
echo ============================================
echo.

rem ---------- 1. 启动 nginx (端口 8866) ----------
set "PORT_8866="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":8866" ^| findstr "LISTENING"') do set "PORT_8866=%%P"
if defined PORT_8866 (
    echo [nginx] 已在运行, PID %PORT_8866%, 跳过启动
) else (
    if exist "%NGINX%\nginx.exe" (
        powershell -NoProfile -Command "Start-Process -FilePath '%NGINX%\nginx.exe' -WorkingDirectory '%NGINX%' -WindowStyle Hidden"
        timeout /t 1 /nobreak >nul
        echo [nginx] 已启动, 站点: http://127.0.0.1:8866
    ) else (
        echo [警告] 未找到 nginx: %NGINX%\nginx.exe
    )
)

rem ---------- 2. 启动 EXE 客户端 ----------
if exist "%DIST%\usmind.exe" (
    start "" "%DIST%\usmind.exe"
    echo [EXE] 客户端已启动
) else (
    echo [提示] 未找到 %DIST%\usmind.exe, 请先运行 build.bat 构建
)

echo.
echo   网页端:  http://127.0.0.1:8866
echo ============================================
exit /b 0
