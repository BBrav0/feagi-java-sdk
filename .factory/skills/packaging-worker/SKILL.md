---
name: packaging-worker
description: Implements Maven publishing configuration, native library loader, CI workflows, and documentation for the FEAGI Java SDK.
---

# Packaging Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Use for features involving:
- Gradle build configuration (maven-publish, signing plugins)
- Maven POM configuration for platform classifiers
- Native library loader implementation (OS/arch detection, classpath extraction)
- GitHub Actions CI workflow for publishing
- Consumer documentation for Maven/Gradle dependencies

## Required Skills

None.

## Work Procedure

### 1. Understand the Feature

Read the feature description, preconditions, expectedBehavior, and verificationSteps carefully. Read `AGENTS.md` for mission boundaries and conventions. Read `.factory/library/architecture.md` for the platform naming convention and artifact structure.

### 2. Read Existing Code

Before writing anything, read the relevant existing files:
- For loader work: `sdk-native/src/main/java/io/feagi/sdk/nativeffi/FeagiNativeLibrary.java`, `FeagiNativeBindings.java`
- For build config: root `build.gradle.kts`, `sdk-core/build.gradle.kts`, `sdk-native/build.gradle.kts`
- For CI: `.github/workflows/publish-maven-central.yml` (if exists)
- For POM: `pom.xml`, `sdk-core/pom.xml`, `sdk-native/pom.xml` (if they exist on the current branch)

### 3. Write Tests First (Red)

For Java code changes (loader enhancement):
- Write JUnit 5 test classes BEFORE implementing
- Tests go in `sdk-native/src/test/java/io/feagi/sdk/nativeffi/`
- Design the loader to be testable: use package-private methods that accept OS name/arch as parameters rather than reading system properties directly
- Mock System.load/loadLibrary since actual native libs are not available
- Run tests to confirm they fail: `.\gradlew.bat :sdk-native:test`

For build configuration:
- Tests are the verification steps themselves (publishToMavenLocal, jar tf, etc.)

### 4. Implement

- Follow existing code style (immutable types, fail-fast, no hardcoded defaults)
- Use `osx` for macOS (never `darwin`) in all classifier names and resource paths
- GroupId: `org.feagi`, Version: `0.0.1`
- Native library name: `feagi_java_ffi`
- Ensure the Gradle signing plugin is conditional (no failure when keys absent)
- Classifier JARs must be secondary artifacts on the base POM (Maven convention)

### 5. Make Tests Pass (Green)

Run tests and iterate until all pass:
```
.\gradlew.bat :sdk-native:test
.\gradlew.bat :sdk-core:test
.\gradlew.bat build
```

### 6. Verify

Run all verification steps from the feature description. For build features:
```
.\gradlew.bat publishToMavenLocal
```
Then inspect the local Maven repo:
- Check `~/.m2/repository/org/feagi/sdk-core/0.0.1/` exists with JAR + POM
- Check `~/.m2/repository/org/feagi/sdk-native/0.0.1/` exists with JAR + POM + classifier JARs
- Use `jar tf` to inspect JAR contents

For CI workflow features: review YAML structure manually.

### 7. Run Full Build

```
.\gradlew.bat build
```
Ensure BUILD SUCCESSFUL with no regressions.

## Example Handoff

```json
{
  "salientSummary": "Enhanced FeagiNativeLibrary with OS/arch detection and classpath extraction. Added 15 unit tests covering all 5 platforms, fallback behavior, error handling, and thread safety. All tests pass (`./gradlew :sdk-native:test` — 15 tests, 0 failures).",
  "whatWasImplemented": "Added PlatformDetector inner class with detectOs(), detectArch(), platformString() methods. Added ClasspathNativeLoader with extract() method that reads from native/<platform>/<libname> resource path, writes to temp file, and calls System.load(). loadAndVerify() now tries classpath extraction first, then falls back to System.loadLibrary(). Shutdown hook registered for temp file cleanup.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {
        "command": ".\\gradlew.bat :sdk-native:test",
        "exitCode": 0,
        "observation": "15 tests passed, 0 failures, 0 skipped"
      },
      {
        "command": ".\\gradlew.bat build",
        "exitCode": 0,
        "observation": "BUILD SUCCESSFUL, all modules compile"
      }
    ],
    "interactiveChecks": []
  },
  "tests": {
    "added": [
      {
        "file": "sdk-native/src/test/java/io/feagi/sdk/nativeffi/PlatformDetectorTest.java",
        "cases": [
          { "name": "detectOs_linux", "verifies": "Linux OS detection returns 'linux'" },
          { "name": "detectOs_macOS", "verifies": "Mac OS X detection returns 'osx'" },
          { "name": "detectOs_windows", "verifies": "Windows detection returns 'windows'" },
          { "name": "detectArch_amd64", "verifies": "amd64 normalized to x86_64" },
          { "name": "detectArch_aarch64", "verifies": "aarch64 detection" }
        ]
      }
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Feature depends on Maven being installed locally (not available)
- Gradle daemon fails to start or has persistent issues
- Upstream POM files are not available on the current branch (need merge first)
- Native binary files are expected but don't exist (this is by design — use placeholder files)
- Ambiguity about which convention to use (osx vs darwin, library naming, etc.)
