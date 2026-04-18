#!/bin/bash
# Environment setup for Maven Packaging mission
# Idempotent — safe to run multiple times

cd "$(dirname "$0")/.." || exit 1

# Verify Java is available
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not on PATH"
    exit 1
fi

echo "Java version:"
java -version 2>&1

# Verify Gradle wrapper exists
if [ ! -f "gradlew" ] && [ ! -f "gradlew.bat" ]; then
    echo "ERROR: Gradle wrapper not found"
    exit 1
fi

echo "Environment ready."
