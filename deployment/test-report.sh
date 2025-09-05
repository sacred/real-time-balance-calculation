#!/bin/bash
cd "$(dirname "$0")/.."

echo "Starting Redis and Postgres..."
podman-compose -f compose/compose.yaml up -d redis postgres

echo "Test and Reporting..."
mvn test jacoco:report
