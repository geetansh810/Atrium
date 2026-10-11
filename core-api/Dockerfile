# syntax=docker/dockerfile:1.7
# core-api image — used by local docker-compose, Render (render.yaml), and CI.
#
# OPS1 (lean Docker): three stages so the runtime image carries only what the
# app needs to run.
#   build  — Maven package with a BuildKit cache mount for ~/.m2, so a pom
#            change re-resolves only what changed instead of re-downloading
#            every dependency.
#   jre    — jlink a minimal Java 21 runtime (only the JDK modules the app
#            actually uses) instead of shipping the full JRE.
#   final  — alpine + that runtime + the Spring Boot jar extracted into
#            layers (dependencies first, app classes last), so a code-only
#            change re-ships only the small application layer.

# ── build ────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# No separate `dependency:go-offline` layer: it resolves every test dep and
# plugin and re-checks remote metadata on each run (~200s even with a warm
# cache). With the ~/.m2 cache mount, `package` resolves only what the build
# needs, and the cache persists across builds (including --no-cache builds).
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B package -DskipTests \
 && java -Djarmode=tools -jar target/core-api-*.jar extract --layers --launcher --destination /app/extracted

# ── jre ──────────────────────────────────────────────────────────────────────
# Module list is explicit, not jdeps-derived: Spring/Hibernate load much of
# their surface reflectively, which jdeps can't see. If a new dependency fails
# at boot with NoClassDefFoundError for a java.*/jdk.* class, add its module here.
#   java.desktop          — java.beans (Spring bean introspection)
#   java.sql / java.naming / java.transaction.xa — JDBC, Hibernate, Hikari
#   java.management / jdk.management — actuator + Micrometer JVM metrics
#   java.net.http         — JdkClientHttpRequestFactory (OpenAiEmbeddingClient)
#   java.instrument       — Hibernate bytecode enhancement / agents
#   jdk.unsupported       — sun.misc.Unsafe (Netty/Lettuce, Jackson)
#   jdk.crypto.ec / jdk.crypto.cryptoki — TLS to LLM providers, managed DB/Redis
#   jdk.zipfs             — reading nested jars
#   jdk.localedata        — non-English Locale data (formatting)
FROM eclipse-temurin:21-jdk-alpine AS jre
RUN jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.management,jdk.naming.dns,jdk.net,jdk.unsupported,jdk.zipfs,jdk.localedata \
      --include-locales=en \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /opt/jre

# ── final ────────────────────────────────────────────────────────────────────
FROM alpine:3.20
ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:$PATH
# Memory: size the heap from the container's limit instead of the host's RAM
# (the JVM default of 25% of the HOST could grow to ~1GB locally and overflow
# Render free's 512MB). ~50% heap leaves room for metaspace/code cache/threads
# (~200MB off-heap measured at idle). SerialGC has the smallest footprint for
# a 1–2 vCPU container. All overridable by setting JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"
RUN addgroup -S atrium && adduser -S atrium -G atrium
COPY --from=jre /opt/jre /opt/jre
WORKDIR /app
COPY --from=build --chown=atrium:atrium /app/extracted/dependencies/ ./
COPY --from=build --chown=atrium:atrium /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=atrium:atrium /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=atrium:atrium /app/extracted/application/ ./
USER atrium
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
