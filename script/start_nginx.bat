@echo off
chcp 65001 >nul
title Nginx 启动
cd /d %~dp0..\tools\nginx-1.30.5
if exist logs\nginx.pid (echo Nginx 已在运行 & goto :eof)
start /b nginx.exe
timeout /t 1 /nobreak >nul
echo Nginx 已启动: http://127.0.0.1:8866
