#!/bin/bash

echo "========================================"
echo "  大猪吉他练习助手 - 一键启动"
echo "========================================"
echo ""

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "[错误] 未检测到 Java，请先安装 Java 8 或更高版本"
    exit 1
fi

# 检查 Maven
if command -v mvn &> /dev/null; then
    MVN_CMD="mvn"
elif [ -f "./mvnw" ]; then
    MVN_CMD="./mvnw"
else
    echo "[错误] 未找到 Maven，请安装 Maven 或下载 mvnw"
    exit 1
fi

echo "[1/3] 正在清理旧文件..."
$MVN_CMD clean > /dev/null 2>&1

echo "[2/3] 正在编译项目..."
$MVN_CMD compile -q
if [ $? -ne 0 ]; then
    echo "[错误] 编译失败"
    exit 1
fi

echo "[3/3] 启动服务..."
echo ""
echo "========================================"
echo "  服务已启动！"
echo "  主页: http://localhost:8080"
echo "  调音器: http://localhost:8080/tuner"
echo "  按 Ctrl+C 可停止服务"
echo "========================================"
echo ""

$MVN_CMD spring-boot:run
