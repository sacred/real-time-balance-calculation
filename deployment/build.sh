#!/bin/bash
WORK_DIR=$(pwd)/../

cd $WORK_DIR
echo "Building JAR package..."
mvn clean package -DskipTests

# 构建Docker镜像
echo "Building Docker image..."
cd $WORK_DIR
docker build -t repo/real-time-balance-calculation:latest .
docker build -t repo/real-time-balance-calculation:1.0.0 .

echo "Docker images built successfully!"
