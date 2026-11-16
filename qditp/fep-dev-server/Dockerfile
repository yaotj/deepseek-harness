FROM os-harbor-svc.default.svc.cloudos:11443/itpa/jdk:21


ENV JAVA_TOOL_OPTIONS="-Djdk.virtualThreadScheduler.parallelism=8 -Djdk.virtualThreadScheduler.maxPoolSize=256"

ARG JAR_FILE=/*.jar

COPY ${JAR_FILE} /app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
