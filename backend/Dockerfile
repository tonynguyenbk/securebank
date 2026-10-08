# One Dockerfile for every backend module:
#   docker build -f backend/Dockerfile --build-arg MODULE=banking-core-service backend/
# Multi-stage: Maven build on a JDK image, run on a slim JRE as a non-root user.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Resolve dependencies first so source edits don't invalidate the dependency layer.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/
COPY identity-service/pom.xml identity-service/
COPY banking-core-service/pom.xml banking-core-service/
COPY fraud-service/pom.xml fraud-service/
COPY audit-service/pom.xml audit-service/
COPY notification-service/pom.xml notification-service/
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline -DexcludeReactor=true || true

ARG MODULE
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp -q -pl ${MODULE} -am package -DskipTests \
    && cp ${MODULE}/target/${MODULE}.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
