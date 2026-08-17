# syntax=docker/dockerfile:1

# ------------------------------------------------------------------------------------------------
# Build stage
# ------------------------------------------------------------------------------------------------
FROM eclipse-temurin:26-jdk AS build
WORKDIR /build

# Dependencies change far less often than source, so resolve them in their own layer. Editing a
# Java file then costs a recompile, not a re-download of the whole dependency tree.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY config/ config/
COPY src/ src/

# Tests run in CI against a real database via Testcontainers, which needs a Docker daemon this
# build does not have. The image build verifies that the application packages, not that it passes.
RUN ./mvnw -B -q clean package -DskipTests -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dspotbugs.skip=true -Djacoco.skip=true

# Spring Boot's layered jar splits dependencies from application code, so a code-only change
# invalidates just the last, smallest layer of the runtime image. The layer directories are meant
# to be merged into one directory: `application/` holds the jar and `dependencies/` holds the
# `lib/` it references relatively from its manifest Class-Path.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted \
    && mv extracted/application/*.jar extracted/application/app.jar

# ------------------------------------------------------------------------------------------------
# Runtime stage
# ------------------------------------------------------------------------------------------------
FROM eclipse-temurin:26-jre AS runtime

# Never run the application as root.
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# The image ships no curl or wget, so the check uses bash's /dev/tcp. It must be bash: /bin/sh is
# dash here, which has no /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

# Boot 4's extracted layout is a plain executable jar beside its lib/ directory — there is no
# JarLauncher involved.
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
