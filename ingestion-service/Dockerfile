# ============================================================================
# ingestion-service — multi-stage container image
# ============================================================================
# WHY the build stage pins gradle:8.x instead of using a wrapper or the host:
# this repo has no Gradle wrapper, and the host global Gradle 9.6.1 cannot
# run the Spring Boot 3.2.5 Gradle plugin (bootJar/bootRun fail locally).
# Pinning a known-good Gradle 8.x inside the image makes the build
# reproducible regardless of the host toolchain.
#
# Tests are intentionally skipped here (-x test): `gradle test` stays the
# local/CI verification, and the ingestion integration test spins up a
# Testcontainers Postgres, which needs a Docker socket that container builds
# don't mount.

FROM gradle:8.10-jdk17 AS build
WORKDIR /work
# .dockerignore keeps build/, .gradle/, .git/ out of the build context
COPY . .
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# curl is used by the docker-compose healthcheck (BusyBox wget lacks --timeout)
RUN apk add --no-cache curl
COPY --from=build /work/build/libs/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]