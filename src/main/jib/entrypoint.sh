#!/bin/sh

exec java $JAVA_OPTS \
  -cp /app/config:/app/resources/:/app/classes:/app/libs/* \
  com.sacred.BalanceCalculationApplication
