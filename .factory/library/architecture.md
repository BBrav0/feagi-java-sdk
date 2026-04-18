# Architecture

Architectural decisions, patterns discovered, and design notes.

---

## Module Structure

| Module | Package | Purpose |
|--------|---------|---------|
| `sdk-core` | `io.feagi.sdk.core` | Public API types — config, capabilities, enums, interfaces. No JNI. |
| `sdk-native` | `io.feagi.sdk.nativeffi` | JNI binding skeleton + native library loader. Depends on `sdk-core`. |
| `sdk-engine` | `io.feagi.sdk.engine` | Engine layer (out of scope for this mission). |
| `sdk-cli` | `io.feagi.sdk.cli` | CLI tool (out of scope for this mission). |

## Maven Publishing Architecture

- **Parent POM** (`feagi-java-sdk-parent`) — aggregator with Maven Central metadata
- **central-publishing-maven-plugin** v0.9.0 — Sonatype Central Portal (newer approach, not legacy OSSRH staging)
- **Release profile** — attaches source JARs, javadoc JARs, GPG signatures
- **Classified JARs** (to be implemented) — platform-specific native lib JARs for sdk-native

## Native Library Loading

Current: `System.loadLibrary()` only — caller must set `java.library.path`
Target: Auto-detect OS/arch → extract from classpath → `System.load()` → fallback to `System.loadLibrary()`

## Test Source Layout

- sdk-core: `tests/` (non-standard, configured via Gradle and Maven build-helper)
- sdk-native: `src/test/java/` (standard Maven layout)
- sdk-engine: `tests/` (non-standard)
- sdk-cli: `tests/` (non-standard)
