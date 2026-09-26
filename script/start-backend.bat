@echo off
title USMIND - Spring Boot Backend
echo ========================================
echo   Start USMIND Backend (Spring Boot)
echo ========================================
echo.

set JAVA_HOME=C:\Users\25750\Desktop\usmind\tools\jdk-17.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
set PROJECT_DIR=C:\Users\25750\Desktop\usmind\code\backend

cd /d "%PROJECT_DIR%"

echo [Build] Maven packaging...
call C:\Users\25750\Desktop\usmind\tools\apache-maven-3.9.6\bin\mvn.cmd clean package -DskipTests -q

if %errorlevel% neq 0 (
    echo [Error] Build failed
    pause
    exit /b 1
)

echo [Start] Backend on port 8899...
java -jar "target\usmind-backend.jar" --spring.profiles.active=dev

echo.
echo [Stopped] Backend exited
pause
