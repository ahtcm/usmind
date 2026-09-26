@echo off
chcp 65001 >nul
title USMIND 启动
cd /d %~dp0..
if not exist "release\USMIND.exe" (echo 未找到 EXE, 请先运行 build_exe.bat & pause & exit /b 1)
start "" "release\USMIND.exe"
echo USMIND 已启动(配置面板将打开)
