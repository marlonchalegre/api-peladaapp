# Multi-stage build for api-peladaapp

# --- Builder image: builds the uberjar
# Using Java 23 to match LibSQL driver requirements
FROM --platform=$BUILDPLATFORM clojure:temurin-23-lein AS builder
WORKDIR /app

ENV LEIN_JVM_OPTS="-XX:MaxRAMPercentage=85.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -DSKIP_DB_INIT=true"

# Cache dependencies first (layer mechanism)
COPY project.clj ./
RUN --mount=type=cache,target=/root/.m2 \
    lein deps

# Copy the rest of the source
COPY . .

# Build an uberjar
# Using the cache for .m2 again during uberjar build
RUN --mount=type=cache,target=/root/.m2 \
    lein uberjar && \
    mv target/uberjar/*-standalone.jar /app/app.jar

# --- Runtime image: lean JRE
FROM eclipse-temurin:23-jre-noble
WORKDIR /app

# Combine apt operations to reduce layers
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && ARCH=$(dpkg --print-architecture) && \
    if [ "$ARCH" = "armhf" ]; then L_ARCH="arm"; else L_ARCH="$ARCH"; fi && \
    curl -L "https://github.com/benbjohnson/litestream/releases/download/v0.3.13/litestream-v0.3.13-linux-${L_ARCH}.deb" -o litestream.deb && \
    dpkg -i litestream.deb && \
    rm litestream.deb && \
    apt-get purge -y --auto-remove curl && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Copy artifacts from builder
COPY --from=builder /app/app.jar /app/app.jar
COPY --from=builder /app/resources /app/resources
COPY litestream.yml /etc/litestream.yml
COPY run.sh /app/run.sh
RUN chmod +x /app/run.sh

EXPOSE 8080

# Production runtime JVM opts
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["/app/run.sh"]
