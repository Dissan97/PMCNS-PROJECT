# ------------------------
# Stage 1: build with Maven
# ------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY . .

RUN mvn -B clean package -DskipTests

# ------------------------
# Stage 2: slim runtime
# ------------------------
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/target/WebAppSimulator.jar ./WebAppSimulator.jar
COPY --from=builder /app/docker-cfgs ./cfgs

# Default command
CMD ["java", "-jar", "WebAppSimulator.jar"]
