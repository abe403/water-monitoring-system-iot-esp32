FROM amazoncorretto:25-alpine

ARG SERVICE
RUN apk add --no-cache libstdc++
WORKDIR /app
COPY platform/${SERVICE}/build/libs/${SERVICE}-0.1.0-SNAPSHOT.jar app.jar

USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
