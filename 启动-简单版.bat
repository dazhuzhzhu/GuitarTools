@echo off
cd /d "%~dp0"
echo 正在启动服务，请稍候...
echo 按 Ctrl+C 可停止
echo.
mvn spring-boot:run
pause
