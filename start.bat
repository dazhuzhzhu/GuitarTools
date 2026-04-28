@echo off
chcp 65001 >nul
echo ========================================
echo   大猪吉他练习助手 - 一键启动
echo ========================================
echo.

:: 检查 Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java，请先安装 Java 8 或更高版本
    echo 下载地址: https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

:: 检查 Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [提示] 未检测到 Maven，正在使用内置 Maven Wrapper...
    if not exist "mvnw.cmd" (
        echo [错误] 未找到 Maven Wrapper，请安装 Maven 或下载 mvnw
        pause
        exit /b 1
    )
    set MVN_CMD=mvnw.cmd
) else (
    set MVN_CMD=mvn
)

echo [1/3] 正在清理旧文件...
call %MVN_CMD% clean >nul 2>&1

echo [2/3] 正在编译项目...
call %MVN_CMD% compile -q
if %errorlevel% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo [3/3] 启动服务...
echo.
echo ========================================
echo   服务已启动！
echo   主页: http://localhost:8080
echo   调音器: http://localhost:8080/tuner
echo   按 Ctrl+C 可停止服务
echo ========================================
echo.

call %MVN_CMD% spring-boot:run

pause
