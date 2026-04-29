@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   大猪吉他练习助手 - 一键启动
echo ========================================
echo.

echo [检查] Java 环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [错误] 未检测到 Java
    echo 请先安装 Java 8 或更高版本
    echo 下载地址: https://www.oracle.com/java/technologies/downloads/
    echo.
    pause
    exit /b 1
)

echo [检查] Maven 环境...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [提示] 未检测到系统 Maven
    if exist "mvnw.cmd" (
        echo [OK] 使用 Maven Wrapper
        set MVN_CMD=mvnw.cmd
    ) else (
        echo.
        echo [错误] 未找到 Maven
        echo 请安装 Maven 3.x 或下载 Maven Wrapper
        echo.
        pause
        exit /b 1
    )
) else (
    echo [OK] Maven 已安装
    set MVN_CMD=mvn
)

echo.
echo ========================================
echo   正在启动，请稍候...
echo ========================================
echo.

%MVN_CMD% clean spring-boot:run

echo.
echo ========================================
echo   服务已停止
echo ========================================
pause
