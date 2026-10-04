# syntax=docker/dockerfile:1
# The awardtrace/backend image: one jar that runs any role (doc 02). Build it from the repository root:
#   docker build -f backend/Dockerfile .
# Tests don't run here; CI runs them before the deploy workflow builds this image.

# The jar is the same on every CPU, so it builds on the build machine's own platform, even for an arm64 image.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /build
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --quiet dependency:go-offline
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw --batch-mode --quiet package -DskipTests \
    && java -Djarmode=tools -jar target/awardtrace-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21.0.12.1_1-jre-noble
RUN useradd --system --no-create-home --shell /usr/sbin/nologin awardtrace
WORKDIR /app
# Least to most often changed, so a deploy usually pushes only the last layer.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER awardtrace
# Heap size comes from JAVA_TOOL_OPTIONS in the Compose file. Roles, profiles, and tasks are arguments:
#   docker run awardtrace/backend --spring.profiles.active=ingest --awardtrace.ingest.task=delta
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
