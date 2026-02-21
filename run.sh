#!/bin/bash
set -e

# Restore database from replica if it doesn't exist
if [ ! -f /app/data/peladaapp.db ]; then
    echo "Database not found, attempting to restore from replica..."
    litestream restore -if-replica-exists -o /app/data/peladaapp.db /app/data/peladaapp.db || echo "No replica found, starting fresh."
fi

# Run the app with litestream replication
echo "Starting Litestream replication and Java application..."
exec litestream replicate -exec "java $JAVA_OPTS -jar /app/app.jar"
