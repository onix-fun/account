FROM eclipse-temurin:23-jre
WORKDIR /app

# Include OpenTelemetry Java agent (expected in build/docker/)
COPY build/docker/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

COPY build/docker/profile.jar /app/profile.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/profile.jar"]
