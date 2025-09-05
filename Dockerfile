FROM openjdk:17

WORKDIR /app
COPY target/balance-system-1.0.0.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xmx256m"
ENV SPRING_PROFILES_ACTIVE=""

LABEL maintainer="wengxk"
LABEL version="1.0.0"

ENTRYPOINT exec java $JAVA_OPTS \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher 
  
