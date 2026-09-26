@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem USMIND 一键构建脚本
rem 用法: build.bat           全量构建(前端 + 静态站点 + EXE)
rem       build.bat web       仅构建前端与静态站点
rem       build.bat exe       仅打包 EXE
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "TOOLS=%ROOT%\tools"
set "PYTHON=%TOOLS%\python310\python.exe"
set "NODE=%TOOLS%\node-v24.21.0-win-x64\node.exe"
set "NPM=%TOOLS%\node-v24.21.0-win-x64\npm.cmd"
set "CODE_WEB=%ROOT%\code\web"
set "CODE_EXE=%ROOT%\code\usmind"
set "LOGS=%ROOT%\logs"
set "DIST=%ROOT%\dist"
set "MODE=%1"
if "%MODE%"=="" set "MODE=all"

if not exist "%LOGS%" mkdir "%LOGS%"
set "BUILD_LOG=%LOGS%\build.log"

echo.
echo ============================================
echo   USMIND 一键构建  [%MODE%]
echo ============================================
echo.

rem ---------- 工具检查 ----------
if not exist "%PYTHON%" ( echo [错误] 未找到 Python: %PYTHON% & exit /b 1 )
if not exist "%NODE%"   ( echo [错误] 未找到 Node:   %NODE%   & exit /b 1 )

rem ---------- 安装 Python 依赖(增量) ----------
if "%MODE%"=="exe" goto BUILD_EXE
if not exist "%CODE_WEB%\package.json" ( echo [错误] 前端工程缺失: %CODE_WEB%\package.json & exit /b 1 )

echo [1/4] 安装前端依赖 npm install ...
pushd "%CODE_WEB%"
call "%NPM%" install >> "%BUILD_LOG%" 2>&1
if errorlevel 1 ( echo [错误] npm install 失败, 详见 %BUILD_LOG% & popd & exit /b 1 )
echo [完成] 前端依赖就绪

echo [2/4] 编译前端 npm run build ...
call "%NPM%" run build >> "%BUILD_LOG%" 2>&1
if errorlevel 1 ( echo [错误] 前端构建失败, 详见 %BUILD_LOG% & popd & exit /b 1 )
popd
echo [完成] 前端编译成功

echo [3/4] 生成静态站点(复制到部署根 usmind/) ...
"%PYTHON%" "%SCRIPT_DIR%build_site.py" >> "%BUILD_LOG%" 2>&1
if errorlevel 1 ( echo [错误] 静态站点生成失败, 详见 %BUILD_LOG% & exit /b 1 )
echo [完成] 静态站点已更新: %ROOT%\usmind

if "%MODE%"=="web" ( echo. & echo 构建完成(web). 访问: http://127.0.0.1:8866 & exit /b 0 )

:BUILD_EXE
echo [4/4] 打包 EXE (PyInstaller) ...
"%PYTHON%" -m pip show pyinstaller >nul 2>&1
if errorlevel 1 "%PYTHON%" -m pip install pyinstaller >> "%BUILD_LOG%" 2>&1
if not exist "%CODE_EXE%\usmind.spec" ( echo [错误] 缺少打包配置: %CODE_EXE%\usmind.spec & exit /b 1 )
pushd "%CODE_EXE%"
"%PYTHON%" -m PyInstaller --noconfirm --clean usmind.spec >> "%BUILD_LOG%" 2>&1
if errorlevel 1 ( echo [错误] EXE 打包失败, 详见 %BUILD_LOG% & popd & exit /b 1 )
popd
if not exist "%DIST%" mkdir "%DIST%"
if exist "%CODE_EXE%\dist\usmind\usmind.exe" (
    copy /y "%CODE_EXE%\dist\usmind\usmind.exe" "%DIST%\usmind.exe" >nul
) else (
    copy /y "%CODE_EXE%\dist\usmind.exe" "%DIST%\usmind.exe" >nul
)
echo [完成] EXE 已生成: %DIST%\usmind.exe

echo.
echo ============================================
echo   构建全部完成
echo   部署根:  %ROOT%\usmind
echo   EXE:     %DIST%\usmind.exe
echo   日志:    %BUILD_LOG%
echo ============================================
exit /b 0
