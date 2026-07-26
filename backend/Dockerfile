# syntax=docker/dockerfile:1

# --- Stage 1: build the executable jar -----------------------------------------------------------
# A full JDK 21 (matching the project toolchain) builds the boot jar. Gradle files are copied first
# so the dependency-download layer is cached and only re-runs when the build script changes.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
# Tests run in CI against real containers, not here — the image build just assembles the artifact.
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Stage 2: minimal runtime --------------------------------------------------------------------
# A JRE-only image (no build tooling) runs as a non-root user. curl is added purely for the
# container healthcheck.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS=""
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
