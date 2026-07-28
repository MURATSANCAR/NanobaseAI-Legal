FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21.0.7_6-jre-alpine
RUN addgroup -S specai && adduser -S -G specai -u 10001 specai
WORKDIR /app
COPY --from=build --chown=specai:specai /workspace/target/specai-*.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8080/actuator/health/liveness \
      | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
