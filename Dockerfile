# =========================================
# 몽글픽 Spring Boot 백엔드 Docker 이미지
# =========================================
# 멀티스테이지 빌드: Gradle 빌드 → JRE 런타임 이미지
# Java 21 기반, 비루트 사용자로 실행

# --- 1단계: Gradle 빌드 ---
FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

# Gradle 설정 파일 복사 (캐시 레이어 활용)
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/

# 의존성 미리 다운로드 (소스 변경 시 캐시 재사용)
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사
COPY src/ src/

# JAR 빌드 (테스트 제외)
RUN gradle bootJar --no-daemon -x test

# --- 2단계: 런타임 이미지 ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# curl: 헬스체크용
# tzdata: Asia/Seoul 시간대 지원 (QA #162/#177 — LocalDateTime naive 직렬화가 UTC 기준이 되는 문제 해소).
#   Alpine 기본 이미지는 tzdata 미포함이라 TZ=Asia/Seoul 만 지정하면 여전히 UTC 로 동작한다.
RUN apk add --no-cache curl tzdata

# JAR 복사 (빌드 결과물)
COPY --from=builder /app/build/libs/*.jar app.jar

# 비루트 사용자 생성 (보안)
RUN adduser -D -s /bin/sh appuser
USER appuser

# 기본 타임존 — docker-compose 에서 TZ 환경변수로 오버라이드 가능.
# JVM 옵션 -Duser.timezone 도 함께 사용해 Jackson/Hibernate/LocalDateTime.now() 전부 KST 기준이 되도록 한다.
ENV TZ=Asia/Seoul

# 헬스체크용 포트 노출
EXPOSE 8080

# Spring Boot 실행
# JVM 메모리 + 타임존 고정. JAVA_OPTS 로 추가 튜닝 가능하며 기본값을 KST 로 못박는다.
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms512m -Xmx1536m} -Duser.timezone=Asia/Seoul -jar app.jar"]
