# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/comforthub-backoffice-1.0.0.jar app.jar

# Render exposes the port in the PORT environment variable
ENV PORT=8080
EXPOSE 8080

# Run with JVM optimizations for Render Free Tier (fast startup, low memory footprint)
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
