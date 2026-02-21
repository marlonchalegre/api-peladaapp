# Multi-stage build for api-peladaapp

# --- Builder image: builds the uberjar
FROM clojure:temurin-21-lein AS builder
WORKDIR /app

# Optimize Leiningen for building on resource-constrained devices like Pi
# -XX:TieredStopAtLevel=1 speeds up JVM startup time during compilation
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
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/app.jar /app/app.jar
COPY --from=builder /app/resources /app/resources

EXPOSE 8080

# Production runtime JVM opts
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java"]
CMD ["-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
