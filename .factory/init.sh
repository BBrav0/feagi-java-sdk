#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

# Ensure Gradle wrapper is executable (no-op on Windows but needed for CI)
if [ -f gradlew ]; then
  chmod +x gradlew 2>/dev/null || true
fi

echo "Init complete — Gradle wrapper ready"
