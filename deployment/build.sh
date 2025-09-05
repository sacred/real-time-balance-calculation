#!/bin/bash
cd "$(dirname "$0")/.."

echo "Building JAR package..."
mvn clean package -DskipTests

echo "Building Docker image..."
docker build -t repo/real-time-balance-calculation:latest .
docker build -t repo/real-time-balance-calculation:1.0.0 .

echo "Docker images built successfully!"

