# User Testing

Testing surface, required testing skills/tools, and resource cost classification.

---

## Validation Surface

This is a **library project** — no web UI, no running services. All validation is CLI/build-based.

### Surfaces

1. **Gradle build** — `gradlew.bat build` compiles and runs all tests
2. **Gradle JAR inspection** — `jar tf <jarfile>` to verify JAR contents
3. **File system inspection** — verify file existence, directory structure
4. **YAML/XML structural inspection** — verify POM and workflow configurations
5. **Git state inspection** — verify `.gitignore` rules, tracked files

### Tools Required

- `gradlew.bat` (Gradle wrapper, in project root)
- `jar` (JDK tool, available)
- `java` (JDK 21, available)
- Standard shell commands for file inspection

### Tools NOT Available

- `mvn` (Maven) — NOT installed locally. Maven configs validated structurally only.
- `cmake` — NOT installed, sdk-native cmake hook must be made conditional

## Validation Concurrency

- **Max concurrent validators: 5** — builds are lightweight (~4 seconds), machine has 32GB RAM and 12 cores
- Each validation surface is purely CPU/disk bound, no network services
- Gradle daemon may be shared across concurrent runs (use `--no-daemon` if needed)
