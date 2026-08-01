# ─── build stage ─────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Gradle wrapper + 빌드 스크립트만 먼저 복사 — 의존성 캐시 hit 향상
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY notification-domain/build.gradle.kts notification-domain/
COPY notification-application/build.gradle.kts notification-application/
COPY notification-adapter-in/build.gradle.kts notification-adapter-in/
COPY notification-adapter-out/build.gradle.kts notification-adapter-out/
COPY notification-bootstrap/build.gradle.kts notification-bootstrap/
COPY e2e-tests/build.gradle.kts e2e-tests/
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 + 빌드
COPY notification-domain notification-domain
COPY notification-application notification-application
COPY notification-adapter-in notification-adapter-in
COPY notification-adapter-out notification-adapter-out
COPY notification-bootstrap notification-bootstrap
RUN ./gradlew --no-daemon :notification-bootstrap:bootJar -x test

# ─── runtime stage ────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# OCI image labels — 레지스트리 / 스캐너 / 배포 도구가 출처를 추적할 수 있게.
# org.opencontainers.image.* 는 OCI 표준 annotation key.
LABEL org.opencontainers.image.title="notification-hub" \
      org.opencontainers.image.description="다채널 알림 발송 hub (PUSH / EMAIL / SMS / KAKAO)" \
      org.opencontainers.image.source="https://github.com/ssa1004/notification-hub" \
      org.opencontainers.image.url="https://github.com/ssa1004/notification-hub" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="ssa1004"

# 보안: OS 패키지 패치 — Alpine libcrypto3/libssl3(openssl) 등 fixable CVE 를
# 이미지 빌드 시점에 끌어온다. 태그 핀만으로는 패치가 안 붙으므로 명시적 upgrade.
RUN apk upgrade --no-cache

# 보안: non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /workspace/notification-bootstrap/build/libs/notification-bootstrap-*-boot.jar app.jar

# Container-aware JVM + virtual threads native (Loom)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
