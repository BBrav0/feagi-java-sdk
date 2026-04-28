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

## Flow Validator Guidance: CLI/Build

### Isolation Rules
- Validators operate read-only on the codebase — no file modifications
- Validators inspect pre-existing build artifacts from `gradlew.bat clean build` (already run)
- Test XML reports are at `<module>/build/test-results/test/TEST-<class>.xml`
- Build output has already been verified at BUILD SUCCESSFUL level

### Validation Approach
- For test-pass assertions: parse the JUnit XML report files and verify 0 failures, 0 errors
- For build-level assertions: check build output (already confirmed BUILD SUCCESSFUL)
- For encoding warning assertions: re-run build with output capture and grep for warnings
- For cmake conditional assertions: verify build output contains the cmake skip message

### Key Paths
- Project root: `C:\Users\bendc\Documents\GitHub\feagi-java-sdk`
- sdk-core test results: `sdk-core/build/test-results/test/`
- sdk-native test results: `sdk-native/build/test-results/test/`
- Gradle wrapper: `gradlew.bat` (Windows)

### Build Output Capture
The most recent build output from `gradlew.bat clean build` showed:
- `BUILD SUCCESSFUL in 1m 27s`
- `24 actionable tasks: 24 executed`
- cmake skip message: `sdk-native: cmake not found on PATH — skipping native JNI build tasks`
- No encoding/unmappable character warnings
- Note: `sdk-core:compileJava` shows `Note: Some input files use unchecked or unsafe operations.` — this is NOT an error, it's an informational note about generic type usage
