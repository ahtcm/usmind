@echo off
setlocal EnableDelayedExpansion
title USMIND 构建（仅便携版）
cd /d "%~dp0"

set "ROOT=%~dp0"
set "RELEASE=%ROOT%release"
set "TOOLS=%ROOT%tools"
set "JDK=%TOOLS%\jdk-17.0.2"
set "JAVAC=%JDK%\bin\javac.exe"
set "JAR=%JDK%\bin\jar.exe"
set "JLINK=%JDK%\bin\jlink.exe"
set "JPACKAGE=%JDK%\bin\jpackage.exe"
set "SRC=%ROOT%code\launcher-src"
set "BUILD_OUT=%RELEASE%\launcher-out"

echo.
echo ============ 构建便携绿色版 usmind-portable ============
set "PORTABLE=%RELEASE%\usmind-portable"
if exist "%PORTABLE%" rmdir /s /q "%PORTABLE%"
mkdir "%PORTABLE%\components" "%PORTABLE%\data" "%PORTABLE%\app" 2>nul

echo   -- 编译启动器 (Main.java) --
if exist "%BUILD_OUT%" rmdir /s /q "%BUILD_OUT%"
mkdir "%BUILD_OUT%\classes"
"%JAVAC%" -encoding UTF-8 -d "%BUILD_OUT%\classes" "%SRC%\launcher\Main.java"
if errorlevel 1 goto :err
"%JAR%" cf "%BUILD_OUT%\launcher.jar" -C "%BUILD_OUT%\classes" .
if errorlevel 1 goto :err
copy /y "%BUILD_OUT%\launcher.jar" "%PORTABLE%\app\launcher.jar" >nul
if errorlevel 1 goto :err

echo   -- 生成精简 JRE (jlink) --
"%JLINK%" --add-modules java.base,java.compiler,java.datatransfer,java.xml,java.prefs,java.desktop,java.instrument,java.logging,java.management,java.security.sasl,java.naming,java.net.http,java.rmi,java.scripting,java.security.jgss,java.transaction.xa,java.sql,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.httpserver,jdk.jfr,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output "%PORTABLE%\runtime"
if errorlevel 1 goto :err

echo   -- 生成 USMIND.exe (jpackage) --
copy /y "%SRC%\logo.ico" "%PORTABLE%\logo.ico" >nul
if errorlevel 1 goto :err
if exist "%RELEASE%\_app" rmdir /s /q "%RELEASE%\_app"
mkdir "%RELEASE%\_app"
copy /y "%BUILD_OUT%\launcher.jar" "%RELEASE%\_app\" >nul
if errorlevel 1 goto :err
if exist "%RELEASE%\_jp" rmdir /s /q "%RELEASE%\_jp"
"%JPACKAGE%" --type app-image --input "%RELEASE%\_app" --main-jar launcher.jar --main-class launcher.Main --runtime-image "%PORTABLE%\runtime" --icon "%SRC%\logo.ico" --name USMIND --dest "%RELEASE%\_jp"
if errorlevel 1 goto :err
copy /y "%RELEASE%\_jp\USMIND\USMIND.exe" "%PORTABLE%\USMIND.exe" >nul
if errorlevel 1 goto :err
copy /y "%RELEASE%\_jp\USMIND\app\USMIND.cfg" "%PORTABLE%\app\USMIND.cfg" >nul
if errorlevel 1 goto :err
rmdir /s /q "%RELEASE%\_jp"
rmdir /s /q "%RELEASE%\_app"

echo   -- 复制组件 (mysql/redis/nginx/backend/init.sql) --
xcopy /e /i /y /q "%TOOLS%\mysql-8.0.36-winx64" "%PORTABLE%\components\mysql" >nul
if errorlevel 1 goto :err
if exist "%PORTABLE%\components\mysql\data" rmdir /s /q "%PORTABLE%\components\mysql\data"
xcopy /e /i /y /q "%TOOLS%\Redis-x64-5.0.14.1" "%PORTABLE%\components\redis" >nul
if errorlevel 1 goto :err
xcopy /e /i /y /q "%TOOLS%\nginx-1.30.5" "%PORTABLE%\components\nginx" >nul
if errorlevel 1 goto :err
copy /y "%SRC%\nginx-portable.conf" "%PORTABLE%\components\nginx\conf\nginx.conf" >nul
if errorlevel 1 goto :err
copy /y "%ROOT%code\backend\target\usmind-backend.jar" "%PORTABLE%\components\" >nul
if errorlevel 1 goto :err
copy /y "%ROOT%sql\init.sql" "%PORTABLE%\components\" >nul
if errorlevel 1 goto :err

echo   -- 构建并部署前端 (web-user / web-admin) --
set "NPM=%TOOLS%\nodejs\npm.cmd"
set "PATH=%TOOLS%\nodejs;%PATH%"
pushd "%ROOT%code\web-user"
if not exist node_modules call "%NPM%" install
call "%NPM%" run build
if errorlevel 1 goto :err
popd
if exist "%PORTABLE%\components\nginx\html\usmind" rmdir /s /q "%PORTABLE%\components\nginx\html\usmind"
mkdir "%PORTABLE%\components\nginx\html\usmind"
xcopy /e /i /y /q "%ROOT%code\web-user\dist\*" "%PORTABLE%\components\nginx\html\usmind\" >nul
if errorlevel 1 goto :err
pushd "%ROOT%code\web-admin"
if not exist node_modules call "%NPM%" install
call "%NPM%" run build
if errorlevel 1 goto :err
popd
if exist "%PORTABLE%\components\nginx\html\admin" rmdir /s /q "%PORTABLE%\components\nginx\html\admin"
mkdir "%PORTABLE%\components\nginx\html\admin"
xcopy /e /i /y /q "%ROOT%code\web-admin\dist\*" "%PORTABLE%\components\nginx\html\admin\" >nul
if errorlevel 1 goto :err

echo.
echo ============================================
echo   便携版构建完成: %PORTABLE%\USMIND.exe
echo ============================================
if /i not "%1"=="-s" pause
exit /b 0

:err
echo.
echo [构建失败] 错误码 %errorlevel%，请检查上方输出定位问题
if /i not "%1"=="-s" pause
exit /b 1
