# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Cache dependencies first (layer cache friendly)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build
COPY src src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: run ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render / Railway inject PORT env var automatically
EXPOSE 8888
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
