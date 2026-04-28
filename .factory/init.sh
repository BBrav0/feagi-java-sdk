#!/bin/bash
# Environment setup for Maven Packaging mission
# Idempotent — safe to run multiple times

echo "Checking Java..."
java -version 2>&1 || echo "WARNING: Java not found"
echo "Environment ready."
