# Environment

Environment variables, external dependencies, and setup notes.

**What belongs here:** Required env vars, external API keys/services, dependency quirks, platform-specific notes.
**What does NOT belong here:** Service ports/commands (use `.factory/services.yaml`).

---

## Build Tools

- **Java:** 21.0.9 LTS (runtime), targeting Java 17 (compilation)
- **Gradle:** Kotlin DSL, wrapper included (`gradlew.bat` on Windows)
- **Maven:** NOT installed locally. POMs exist for Maven Central publishing but cannot be executed locally.
- **cmake:** NOT required for standard builds. sdk-native has cmake hooks for JNI bridge compilation against Rust FFI, but these must be made conditional.

## Dual Build System

- **Gradle** is used for local development, compilation, and testing
- **Maven** (pom.xml) is used exclusively for Maven Central publishing via GitHub Actions CI
- Both build systems must stay in sync (version, groupId, module structure)

## Native Libraries

- Pre-built native libs (stub placeholders, 21-36 bytes) exist for 5 platforms
- Real native libs come from building the `feagi-java-ffi` Rust project (separate repo/build)
- Location: `sdk-native/src/main/resources/native/<platform>/` (relocated from test resources)
- Copies also remain at `sdk-native/src/test/resources/native/<platform>/` for test compatibility
- `.gitignore` has exception rules to allow tracking native libs in `src/main/resources/native/`

## Secrets (CI only, not needed locally)

- `OSSRH_USERNAME` — Sonatype OSSRH username
- `OSSRH_TOKEN` — Sonatype OSSRH token
- `MAVEN_GPG_PRIVATE_KEY` — Base64-encoded GPG private key
- `MAVEN_GPG_PASSPHRASE` — GPG key passphrase
