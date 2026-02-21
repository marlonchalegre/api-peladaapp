# Multi-stage build for api-peladaapp

# --- Builder image: builds the uberjar
# Using Java 23 to match LibSQL driver requirements
FROM clojure:temurin-23-lein AS builder
WORKDIR /app

ENV LEIN_JVM_OPTS="-XX:MaxRAMPercentage=85.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -DSKIP_DB_INIT=true"

# Cache dependencies first
COPY project.clj ./
RUN --mount=type=cache,target=/root/.m2 \
    lein deps

# Copy the rest of the source
COPY . .

# Build an uberjar
RUN --mount=type=cache,target=/root/.m2 \
    lein uberjar \
    && mv target/uberjar/*-standalone.jar /app/app.jar

# --- Runtime image: lean JRE
# Using Java 23 to support classes compiled with version 67.0
FROM eclipse-temurin:23-jre
WORKDIR /app

COPY --from=builder /app/app.jar /app/app.jar
COPY --from=builder /app/resources /app/resources

EXPOSE 8080

# Production runtime JVM opts
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java"]
CMD ["-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
