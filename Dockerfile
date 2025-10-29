# Multi-stage build for api-100folego

# --- Builder image: builds the uberjar
FROM clojure:temurin-21-lein AS builder
WORKDIR /app

# Cache dependencies first
COPY project.clj ./
RUN lein deps

# Copy the rest of the source
COPY . .

# Build an uberjar and normalize name to app.jar
RUN lein uberjar \
    && ls -l target/uberjar \
    && mv target/uberjar/*-standalone.jar /app/app.jar

# --- Runtime image: lean JRE to run the jar
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy packaged app
COPY --from=builder /app/app.jar /app/app.jar
# Copy runtime resources for config.json access (code reads from filesystem)
COPY --from=builder /app/resources /app/resources

# The app listens on 8080 (see components.clj)
EXPOSE 8080

# Optional: reduce JVM noise, set timezone if desired
ENV JAVA_OPTS="-XX:+UseContainerSupport"

# Run the service
CMD ["sh", "-lc", "exec java $JAVA_OPTS -jar /app/app.jar"]
