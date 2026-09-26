@echo off
title USMIND - Redis
echo ========================================
echo   Start Redis 5.0
echo ========================================
echo.

set REDIS_DIR=C:\Users\25750\Desktop\usmind\tools\Redis-x64-5.0.14.1

echo [Start] Redis...
start "USMIND-Redis" "%REDIS_DIR%\redis-server.exe" "%REDIS_DIR%\redis.windows.conf"

timeout /t 2 /nobreak >nul
echo [Done] Redis started (port 6379)
echo.
