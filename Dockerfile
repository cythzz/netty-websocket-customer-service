FROM maven:3.9.11-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/target/netty-websocket-customer-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
