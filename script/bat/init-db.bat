@echo off
chcp 65001 >nul
title USMIND - 初始化数据库
echo ========================================
echo   初始�?USMIND 数据�?
echo ========================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.."
set "ROOT=%CD%"
set "MYSQL_DIR=%ROOT%\tools\mysql-8.0.36-winx64"
set "SQL_FILE=%ROOT%\sql\init.sql"

REM 先尝试用空密码设�?root 密码�?root（首次初始化时）
"%MYSQL_DIR%\bin\mysql.exe" -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';" 2>nul

echo [执行] 导入 init.sql ...
"%MYSQL_DIR%\bin\mysql.exe" -u root -proot < "%SQL_FILE%"

if %errorlevel% equ 0 (
    echo [完成] 数据�?usmind_db 初始化成�?
    echo   - 数据库账�? root / root
    echo   - 默认管理�? admin / admin123
    echo   - 默认存储: 本地存储
) else (
    echo [错误] 数据库初始化失败，请检�?MySQL 是否已启�?
)
echo.
pause