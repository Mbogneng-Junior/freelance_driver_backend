# --- Stage 1: Build a lightweight production JAR ---
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy only necessary maven wrapper files first for better caching
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Download dependencies. This layer is cached as long as pom.xml doesn't change.
RUN ./mvnw dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Build the application JAR
RUN ./mvnw clean package -DskipTests


# --- Stage 2: Create a minimal and secure final image ---
FROM eclipse-temurin:21-jre-alpine

# Set arguments for user and group
ARG APP_GROUP=yowyob
ARG APP_USER=yowyob

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S $APP_GROUP && adduser -S $APP_USER -G $APP_GROUP

# Copy the built JAR from the builder stage with the correct owner
COPY --from=builder --chown=$APP_USER:$APP_GROUP /app/target/*.jar app.jar

# Switch to the non-root user
USER $APP_USER:$APP_GROUP


# Expose ports for the application API and WebSocket server
EXPOSE 8080 9092

# Entrypoint to run the application
# Using exec form is a best practice for signal handling
ENTRYPOINT ["java", "-jar", "/app/app.jar"]