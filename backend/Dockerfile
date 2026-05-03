FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Python3 + pip 설치 (garminconnect 라이브러리용)
RUN apk add --no-cache python3 py3-pip && \
    pip3 install --break-system-packages garminconnect>=0.3.0

COPY --from=builder /app/target/*.jar app.jar
COPY scripts ./scripts

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
