# syntax=docker/dockerfile:1

# --- Build stage: Maven wrapper on a full JDK ---
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/bulletin-service-*.jar /workspace/app.jar

# --- Runtime stage: JRE only, non-root user ---
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
