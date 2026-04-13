# User Testing

Testing surface, required testing skills/tools, and resource cost classification per surface.

---

## Validation Surface

This mission has NO running application to test. All validation is build-output-based:

1. **Gradle build output** — `./gradlew build` succeeds
2. **Unit test results** — `./gradlew :sdk-core:test :sdk-native:test` passes
3. **Local Maven repo artifacts** — `./gradlew publishToMavenLocal` produces correct artifacts in `~/.m2/repository/org/feagi/`
4. **JAR content inspection** — `jar tf` on published JARs to verify contents
5. **POM content inspection** — XML review of generated POMs
6. **CI workflow YAML** — structural review of `.github/workflows/publish-maven-central.yml`

### Testing Tools
- `./gradlew.bat` (Windows) or `./gradlew` (Unix) for builds
- `jar tf` for inspecting JAR contents
- File system inspection of `~/.m2/repository/org/feagi/`
- Text inspection of POM XML files

### Surfaces NOT Testable
- Actual Maven Central publishing (requires secrets + upstream repo)
- Native library loading (no compiled native binaries available)
- CI workflow execution (requires GitHub Actions runner)

## Validation Concurrency

All validation is local command execution — no resource-heavy services.
- **Max concurrent validators:** 5 (build commands are lightweight)
- **Rationale:** Gradle daemon is shared, commands are sequential anyway
