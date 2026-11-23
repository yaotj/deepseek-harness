#!/bin/bash
# 设备心跳服务独立构建脚本

set -e

echo "======================================"
echo "构建 device-heartbeat-server"
echo "======================================"

# 进入模块目录
cd "$(dirname "$0")"

# 清理并编译
echo "[1/3] 清理..."
mvn clean -o

echo "[2/3] 编译..."
mvn compile -o

echo "[3/3] 打包..."
mvn package -DskipTests -o

echo ""
echo "======================================"
echo "构建完成！"
echo "JAR 文件位置: target/device-heartbeat-server-1.0.0.jar"
echo "======================================"

# 显示 JAR 信息
if [ -f "target/device-heartbeat-server-1.0.0.jar" ]; then
    ls -lh target/device-heartbeat-server-1.0.0.jar
    echo ""
    echo "启动命令:"
    echo "  java -jar target/device-heartbeat-server-1.0.0.jar"
else
    echo "错误：JAR 文件未生成"
    exit 1
fi
