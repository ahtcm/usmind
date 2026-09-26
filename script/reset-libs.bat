@echo off
setlocal enabledelayedexpansion
rem ============================================================
rem USMIND 清除并重新下载依赖库
rem 功能: 清理 Python 第三方库 + 前端 node_modules + 构建缓存, 然后重新安装
rem 用法: reset-libs.bat           全部重置
rem       reset-libs.bat python    仅重置 Python 依赖
rem       reset-libs.bat web       仅重置前端依赖
rem ============================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"
set "PYTHON=%ROOT%\tools\python310\python.exe"
set "NPM=%ROOT%\tools\node-v24.21.0-win-x64\npm.cmd"
set "CODE_WEB=%ROOT%\code\web"
set "CODE_EXE=%ROOT%\code\usmind"
set "MODE=%1"
if "%MODE%"=="" set "MODE=all"

echo.
echo ============================================
echo   USMIND 依赖重置  [%MODE%]
echo ============================================
echo.

rem ---------- 1. 清除 Python 第三方库 ----------
if "%MODE%"=="all" goto PY_CLEAR
if "%MODE%"=="python" goto PY_CLEAR
goto WEB_CLEAR

:PY_CLEAR
echo [1/2] 清除 Python 第三方库 ...
if not exist "%PYTHON%" ( echo [错误] 未找到 Python & goto WEB_CHECK )
"%PYTHON%" -m pip freeze > "%TEMP%\usmind_pip_list.txt" 2>nul
for /f "delims==" %%P in ('type "%TEMP%\usmind_pip_list.txt"') do (
    set "PKG=%%P"
    if not "!PKG!"=="pip" if not "!PKG!"=="setuptools" (
        "%PYTHON%" -m pip uninstall -y !PKG! >nul 2>&1
    )
)
del "%TEMP%\usmind_pip_list.txt" >nul 2>&1
"%PYTHON%" -m pip cache purge >nul 2>&1
echo [完成] Python 第三方库已清除

:WEB_CHECK
if "%MODE%"=="python" ( echo. & echo 重置完成(python). & exit /b 0 )

rem ---------- 2. 清除前端依赖 ----------
:WEB_CLEAR
echo [2/2] 清除前端 node_modules 与构建缓存 ...
if exist "%CODE_WEB%\node_modules" (
    rmdir /s /q "%CODE_WEB%\node_modules"
    echo [完成] node_modules 已删除
)
if exist "%CODE_WEB%\dist" (
    rmdir /s /q "%CODE_WEB%\dist"
    echo [完成] 前端构建缓存 dist 已删除
)
if exist "%CODE_EXE%\build" (
    rmdir /s /q "%CODE_EXE%\build"
    echo [完成] PyInstaller build 缓存已删除
)
if exist "%CODE_EXE%\dist" (
    rmdir /s /q "%CODE_EXE%\dist"
    echo [完成] PyInstaller dist 缓存已删除
)

echo.
echo ---------- 重新下载依赖 ----------
if exist "%CODE_WEB%\package.json" (
    pushd "%CODE_WEB%"
    call "%NPM%" install
    if errorlevel 1 ( echo [错误] 前端依赖安装失败 & popd & exit /b 1 )
    popd
    echo [完成] 前端依赖已重新安装
)
if "%MODE%"=="web" ( echo. & echo 重置完成(web). & exit /b 0 )

echo.
echo ============================================
echo   依赖重置完成
echo ============================================
exit /b 0
