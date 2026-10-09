# syntax=docker/dockerfile:1.7
# One Dockerfile for every backend module:
#   docker build -f backend/Dockerfile --build-arg MODULE=banking-core-service backend/
# Multi-stage: Maven build on a JDK image, run on a slim JRE as a non-root user.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
ARG MODULE
# The Maven repository is a BuildKit cache shared by all module builds, so dependencies download once.
# Retries make the build tolerant of flaky network transfers.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    chmod +x mvnw && \
    for attempt in 1 2 3; do \
      ./mvnw -B -ntp -q -pl "${MODULE}" -am package -Dmaven.test.skip=true \
        -Dmaven.wagon.http.retryHandler.count=5 -Daether.connector.http.retryHandler.count=5 && break; \
      echo "Maven build attempt ${attempt} failed, retrying"; sleep 5; \
    done && \
    cp "${MODULE}/target/${MODULE}.jar" /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
