# 使用阿里云Anolis OS JDK镜像
FROM anolis-registry.cn-zhangjiakou.cr.aliyuncs.com/openanolis/openjdk:17-8.6

# 设置工作目录
WORKDIR /app

# 复制应用JAR文件
COPY target/balance-system-1.0.0.jar app.jar

# 暴露端口
EXPOSE 8080

# 设置环境变量
ENV JAVA_OPTS="-Xmx256m"
ENV SPRING_PROFILES_ACTIVE=""

# 设置标签
LABEL maintainer="wengxk"
LABEL version="1.0.0"

# 直接在ENTRYPOINT中定义启动命令
ENTRYPOINT exec java $JAVA_OPTS \
  -cp /app/config:/app/resources/:/app/classes:/app/libs/*:/app/app.jar \
  com.sacred.BalanceCalculationApplication
