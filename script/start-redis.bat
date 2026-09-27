@echo off
title USMIND - Redis
echo ========================================
echo   Start Redis 5.0
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%.."
set "ROOT=%CD%"
set "REDIS_DIR=%ROOT%\tools\Redis-x64-5.0.14.1"

cd /d "%REDIS_DIR%"
echo [Start] Redis...
start "USMIND-Redis" "redis-server.exe" "redis.windows.conf"

ping -n 3 127.0.0.1 >nul
echo [Done] Redis started (port 6379)
echo.