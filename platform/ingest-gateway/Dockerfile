FROM amazoncorretto:25-alpine
WORKDIR /app
COPY platform/ingest-gateway/build/libs/ingest-gateway-0.1.0-SNAPSHOT.jar app.jar
USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
