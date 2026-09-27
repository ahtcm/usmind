@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title USMIND 一键构建（便携版 / 自解压版 / 在线版）
cd /d "%~dp0"

set "ROOT=%~dp0"
set "RELEASE=%ROOT%release"
set "TOOLS=%ROOT%tools"
set "JDK=%TOOLS%jdk-17.0.2"
set "JAVAC=%JDK%\bin\javac.exe"
set "JAR=%JDK%\bin\jar.exe"
set "JLINK=%JDK%\bin\jlink.exe"
set "JPACKAGE=%JDK%\bin\jpackage.exe"
set "CSC=%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "SRC=%RELEASE%\launcher-src"

if not exist "%CSC%" (
  echo [错误] 未找到 .NET Framework csc.exe，请确认已安装 .NET Framework 4.x
  pause & exit /b 1
)

:: ================= [1/3] 便携绿色版 =================
echo.
echo ============ [1/3] 便携绿色版 usmind-portable ============
set "PORTABLE=%RELEASE%\usmind-portable"
if exist "%PORTABLE%" rmdir /s /q "%PORTABLE%"
mkdir "%PORTABLE%\components" "%PORTABLE%\data" "%PORTABLE%\app" 2>nul

echo   -- 编译启动器 (Main.java) --
"%JAVAC%" -encoding UTF-8 -d "%SRC%\classes" "%SRC%\launcher\Main.java"
if errorlevel 1 goto :err

"%JAR%" cf "%PORTABLE%\app\launcher.jar" -C "%SRC%\classes" .
if errorlevel 1 goto :err

echo   -- 生成精简 JRE (jlink) --
"%JLINK%" --add-modules java.base,java.compiler,java.datatransfer,java.xml,java.prefs,java.desktop,java.instrument,java.logging,java.management,java.security.sasl,java.naming,java.net.http,java.rmi,java.scripting,java.security.jgss,java.transaction.xa,java.sql,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.httpserver,jdk.jfr,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=2 --output "%PORTABLE%\runtime"
if errorlevel 1 goto :err

echo   -- 生成 USMIND.exe (jpackage) --
if exist "%RELEASE%\_jp" rmdir /s /q "%RELEASE%\_jp"
"%JPACKAGE%" --type app-image --input "%PORTABLE%\app" --main-jar launcher.jar --main-class launcher.Main --runtime-image "%PORTABLE%\runtime" --name USMIND --dest "%RELEASE%\_jp"
if errorlevel 1 goto :err
copy /y "%RELEASE%\_jp\USMIND\USMIND.exe" "%PORTABLE%\USMIND.exe" >nul
if errorlevel 1 goto :err
rmdir /s /q "%RELEASE%\_jp"

echo   -- 复制组件 (mysql/redis/nginx/backend/init.sql) --
xcopy /e /i /y /q "%TOOLS%\mysql-8.0.36-winx64" "%PORTABLE%\components\mysql" >nul
if errorlevel 1 goto :err
if exist "%PORTABLE%\components\mysql\data" rmdir /s /q "%PORTABLE%\components\mysql\data"
xcopy /e /i /y /q "%TOOLS%\Redis-x64-5.0.14.1" "%PORTABLE%\components\redis" >nul
if errorlevel 1 goto :err
xcopy /e /i /y /q "%TOOLS%\nginx-1.30.5" "%PORTABLE%\components\nginx" >nul
if errorlevel 1 goto :err
copy /y "%ROOT%code\backend\target\usmind-backend.jar" "%PORTABLE%\components\" >nul
if errorlevel 1 goto :err
copy /y "%ROOT%sql\init.sql" "%PORTABLE%\components\" >nul
if errorlevel 1 goto :err

echo   [完成] 便携版: %PORTABLE%

:: ================= [2/3] 单文件自解压版 =================
echo.
echo ============ [2/3] 单文件自解压版 USMIND-setup.exe ============
echo   -- 压缩便携版 (Optimal, 耗时较长) --
if exist "%RELEASE%\_setup.zip" del "%RELEASE%\_setup.zip"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::CreateFromDirectory('%PORTABLE%', '%RELEASE%\_setup.zip', [System.IO.Compression.CompressionLevel]::Optimal, $false)"
if errorlevel 1 goto :err

echo   -- 编译自解压 stub --
"%CSC%" /nologo /target:winexe /out:"%SRC%\USMIND-setup-stub.exe" /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll "%SRC%\selfextract.cs"
if errorlevel 1 goto :err

echo   -- 拼接 stub + zip --
copy /b "%SRC%\USMIND-setup-stub.exe" + "%RELEASE%\_setup.zip" "%RELEASE%\USMIND-setup.exe" >nul
if errorlevel 1 goto :err
del "%RELEASE%\_setup.zip"
echo   [完成] 自解压版: %RELEASE%\USMIND-setup.exe

:: ================= [3/3] 在线安装版 =================
echo.
echo ============ [3/3] 在线安装版 USMIND-online.exe ============
echo   -- 生成环境分片 (build_online.ps1) --
powershell -NoProfile -ExecutionPolicy Bypass -File "%SRC%\build_online.ps1"
if errorlevel 1 goto :err

echo   -- 编译在线下载器 (Installer.cs) --
"%CSC%" /nologo /target:winexe /out:"%SRC%\USMIND-online-stub.exe" /res:"%RELEASE%\online-dist\manifest.json",Installer.manifest /r:System.Web.Extensions.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll "%SRC%\Installer.cs"
if errorlevel 1 goto :err

copy /y "%SRC%\USMIND-online-stub.exe" "%RELEASE%\USMIND-online.exe" >nul
if errorlevel 1 goto :err
echo   [完成] 在线版: %RELEASE%\USMIND-online.exe

echo.
echo ============================================
echo   全部构建完成
echo   - 便携版   : %RELEASE%\usmind-portable\USMIND.exe
echo   - 自解压版 : %RELEASE%\USMIND-setup.exe
echo   - 在线版   : %RELEASE%\USMIND-online.exe
echo ============================================
pause
exit /b 0

:err
echo.
echo [构建失败] 错误码 %errorlevel%，请检查上方输出定位问题
pause
exit /b 1
