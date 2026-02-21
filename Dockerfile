# Multi-stage build for api-peladaapp

# --- Builder image: builds the uberjar
FROM clojure:temurin-21-lein AS builder
WORKDIR /app

# Cache dependencies first - use BuildKit cache for Maven/Lein repository
COPY project.clj ./
RUN --mount=type=cache,target=/root/.m2 \
    lein deps

# Copy the rest of the source
COPY . .

# Build an uberjar and normalize name to app.jar
# Use the same cache for the uberjar build to avoid re-downloads
ENV LEIN_JVM_OPTS="-DSKIP_DB_INIT=true"
RUN --mount=type=cache,target=/root/.m2 \
    lein uberjar \
    && ls -l target/uberjar \
    && mv target/uberjar/*-standalone.jar /app/app.jar

# --- Runtime image: lean JRE to run the jar
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy packaged app
COPY --from=builder /app/app.jar /app/app.jar
# Copy runtime resources for config.json access
COPY --from=builder /app/resources /app/resources

# The app listens on 8080 (see components.clj)
EXPOSE 8080

# Optional: reduce JVM noise, set memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the service using exec form (no shell) to prevent runtime dependency checks
ENTRYPOINT ["java"]
CMD ["-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
