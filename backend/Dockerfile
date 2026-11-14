FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
# pom.xml만 먼저 복사해 의존성 레이어를 캐시한다
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:21.0.10_7-jre-alpine
WORKDIR /app

# Python3 + pip (garminconnect). wget(busybox)은 HEALTHCHECK용으로 기본 포함
RUN apk add --no-cache python3 py3-pip && \
    pip3 install --no-cache-dir --break-system-packages 'garminconnect==0.3.2' && \
    addgroup -S -g 10001 pios && adduser -S -u 10001 -G pios -h /app pios && \
    mkdir -p /app/data/garmin-tokens && chown -R pios:pios /app

COPY --from=builder --chown=pios:pios /app/target/*.jar app.jar
COPY --chown=pios:pios scripts ./scripts

USER pios
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
