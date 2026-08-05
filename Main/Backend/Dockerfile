FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew \
    && ./gradlew --no-daemon dependencies

COPY src ./src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-noble AS runtime

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes \
        --no-install-recommends \
        --only-upgrade \
        libc6 \
        libc-bin \
        curl \
        libcurl4t64 \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app \
        --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/build/libs/*.jar ./app.jar
COPY --chown=app:app --chmod=0444 certs/ap-northeast-2-bundle.pem /app/certs/rds-ca-bundle.pem

RUN chmod 0755 /app/certs

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.security.egd=file:/dev/urandom"

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
