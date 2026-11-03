FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew gradlew.bat build.gradle settings.gradle ./
RUN chmod +x gradlew \
    && ./gradlew dependencies --configuration productionRuntimeClasspath --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S justgo && adduser -S justgo -G justgo

WORKDIR /app
COPY --from=builder --chown=justgo:justgo /workspace/build/libs/*.jar ./app.jar

USER justgo
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
