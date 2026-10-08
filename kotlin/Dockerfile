# 빌드 단계 — wrapper로 bootJar 생성 (Gradle은 wrapper가 내려받는다)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
# BuildKit 캐시로 빌드 간 재사용 — ~/.gradle(wrapper·의존성)은 재다운로드를, build/·.gradle/은 증분 컴파일을 위해.
# 캐시 마운트는 이미지 레이어에 남지 않으므로 jar는 같은 RUN에서 밖으로 복사한다
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/workspace/build \
    --mount=type=cache,target=/workspace/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon bootJar && cp build/libs/cineseek.jar /cineseek.jar

# 실행 단계 — JRE만 담는다
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /cineseek.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
