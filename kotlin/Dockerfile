# 빌드 단계 — wrapper로 bootJar 생성 (Gradle은 wrapper가 내려받는다)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies -q > /dev/null || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# 실행 단계 — JRE만 담는다
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/cineseek.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
