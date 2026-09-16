# ============================================================================
# analytics-service — multi-stage container image
# ============================================================================
# WHY the build stage pins gradle:8.x: no Gradle wrapper exists in this repo
# and the host global Gradle 9.6.1 cannot run the Spring Boot 3.2.5 plugin
# (see ingestion-service/Dockerfile for the full rationale). Tests are
# skipped here (-x test) — `gradle test` is the local/CI verification layer.

FROM gradle:8.10-jdk17 AS build
WORKDIR /work
COPY . .
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /work/build/libs/*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]