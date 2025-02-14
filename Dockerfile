# Use an official Maven image to build the project
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .
COPY src ./src

# Build the Dropwizard application
RUN mvn clean package -DskipTests

# Use a lightweight JDK image for running the app
FROM eclipse-temurin:21-jdk

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/sockkeeper_v2_poc-1.0-SNAPSHOT.jar app.jar
COPY src/main/resources/config.yml config.yml

# Expose ports
EXPOSE 8888 8889

# Run the application
CMD ["java", "-jar", "app.jar", "server", "config.yml"]