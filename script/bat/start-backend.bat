@echo off
title USMIND - Spring Boot Backend
echo ========================================
echo   Start USMIND Backend (Spring Boot)
echo   (relative paths)
echo ========================================
echo.

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"

set "JAVA_HOME=%ROOT%\tools\jdk-17.0.2"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PROJECT_DIR=%ROOT%\code\backend"
set "MAVEN_CMD=%ROOT%\tools\apache-maven-3.9.6\bin\mvn.cmd"

cd /d "%PROJECT_DIR%"

echo [Build] Maven packaging...
call "%MAVEN_CMD%" clean package -DskipTests -q

if %errorlevel% neq 0 (
    echo [Error] Build failed
    pause
    exit /b 1
)

echo [Start] Backend on port 8899...
java -jar "target\usmind-backend.jar" --spring.profiles.active=dev --file.upload-dir="%ROOT%\uploads"

echo.
echo [Stopped] Backend exited
pause