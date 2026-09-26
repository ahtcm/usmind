@echo off
chcp 65001 >nul
title USMIND 一键构建
cd /d %~dp0..
echo ============================================
echo  [1/3] 构建网页前端 ...
echo ============================================
pushd code\web
call "%~dp0..\tools\node-v24.21.0-win-x64\npm.cmd" run build
popd
if errorlevel 1 (echo 网页构建失败 & pause & exit /b 1)
echo.
echo ============================================
echo  [2/3] 打包 EXE ...
echo ============================================
"%~dp0..\tools\python310\python.exe" code\usmind\build_exe.py
if errorlevel 1 (echo EXE 打包失败 & pause & exit /b 1)
echo.
echo ============================================
echo  [3/3] 完成: release\USMIND.exe
echo ============================================
pause
