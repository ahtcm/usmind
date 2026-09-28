@echo off
title USMIND - Build Backend Jar
echo ========================================
echo   Build backend jar (relative paths)
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "JAVA_HOME=%ROOT%\tools\jdk-17.0.2"
set "MAVEN_CMD=%ROOT%\tools\apache-maven-3.9.6\bin\mvn.cmd"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [Env] JAVA_HOME=%JAVA_HOME%
echo [Env] Maven=%MAVEN_CMD%
echo [Build] mvn clean package -DskipTests ...
echo.

cd /d "%ROOT%\code\backend"
call "%MAVEN_CMD%" clean package -DskipTests

if %errorlevel% neq 0 (
    echo.
    echo [Error] Build failed
    echo [Hint] If jar is locked, stop backend first: stop-backend.bat / stop-all.bat, then retry.
    echo.
    pause
    exit /b 1
)

echo.
echo [Done] jar: %ROOT%\code\backend\target\usmind-backend.jar
echo.
pause
