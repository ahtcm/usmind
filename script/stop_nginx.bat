@echo off
chcp 65001 >nul
title Nginx ¹Ø±Õ
cd /d %~dp0..\tools\nginx-1.30.5
nginx.exe -s quit >nul 2>&1
echo Nginx ÒÑ¹Ø±Õ
