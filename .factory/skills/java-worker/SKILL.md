---
name: java-worker
description: Implements Java source and test code changes for the FEAGI Java SDK
---

# Java Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Use for features that involve:
- Fixing or writing Java test files
- Modifying Java source files (e.g., FeagiNativeLibrary.java)
- Adding new Java classes or methods
- Any feature where the primary deliverable is Java code

## Required Skills

None.

## Work Procedure

1. **Read the feature description carefully.** Understand preconditions, expected behavior, and verification steps.

2. **Read AGENTS.md** for coding conventions, boundaries, and constraints.

3. **Investigate affected files.** Before making changes, read the current source and test files you'll be modifying. Understand the existing patterns, imports, and API surface.

4. **Write/fix tests first (Red).** 
   - For test fix features: read the current test file, identify all compilation errors by cross-referencing with the current source API, then make all necessary changes in one pass.
   - For new features: write failing tests first that cover the expected behavior from the feature description.
   - Compile tests to verify they either fail as expected (new) or now compile (fixes): `cd /d C:\Users\bendc\Documents\GitHub\feagi-java-sdk && gradlew.bat :sdk-core:compileTestJava` or `:sdk-native:compileTestJava`.

5. **Implement source changes (Green).**
   - Make the minimum changes needed to pass tests.
   - Follow existing code patterns — match indentation, naming conventions, javadoc style.
   - For FeagiNativeLibrary changes: preserve thread safety (`volatile` + `synchronized`), preserve existing `load(String)` and `loadAndVerify(String)` behavior.

6. **Run tests to verify green.**
   - `cd /d C:\Users\bendc\Documents\GitHub\feagi-java-sdk && gradlew.bat build`
   - If sdk-native cmake hook blocks the build, skip sdk-native build and test sdk-core: `gradlew.bat :sdk-core:test`
   - Record exact command, exit code, and test count in handoff.

7. **Manual verification.**
   - For test fixes: verify the specific test class passes by running it individually.
   - For JAR-related features: inspect JAR contents with `jar tf <path>`.
   - For native loader: verify the loader class compiles and new methods exist.

8. **Check for regressions.** Run the full module test suite, not just the changed tests.

## Example Handoff

```json
{
  "salientSummary": "Fixed 24 compilation errors in MotorUnitConfigTest by updating minRange()->minValue(), maxRange()->maxValue(), removing bidirectional references, and aligning default value assertions. All 18 remaining tests pass.",
  "whatWasImplemented": "Updated MotorUnitConfigTest.java: renamed accessor calls (minRange->minValue, maxRange->maxValue in 4 tests), removed bidirectional builder calls and assertions (6 tests), removed shouldNotBeEqualWhenBidirectionalDiffers test entirely, updated default range assertion from (0,180) to (0,1), updated toString test to match current output format.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {
        "command": "gradlew.bat :sdk-core:compileTestJava",
        "exitCode": 0,
        "observation": "Compilation successful, 0 errors"
      },
      {
        "command": "gradlew.bat :sdk-core:test --tests io.feagi.sdk.core.MotorUnitConfigTest",
        "exitCode": 0,
        "observation": "18 tests passed, 0 failed, 0 skipped"
      },
      {
        "command": "gradlew.bat :sdk-core:test",
        "exitCode": 0,
        "observation": "All 142 tests passed, 0 failures"
      }
    ],
    "interactiveChecks": []
  },
  "tests": {
    "added": [],
    "modified": [
      {
        "file": "sdk-core/tests/io/feagi/sdk/core/MotorUnitConfigTest.java",
        "cases": [
          {"name": "shouldCreateDefaultMotorUnitConfig", "verifies": "Default builder produces valid config with current defaults"},
          {"name": "shouldCreateMotorUnitConfigForServoMotor", "verifies": "Servo motor config uses minValue/maxValue"}
        ]
      }
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Feature depends on a source file or API that doesn't exist yet
- Test failures indicate a bug in the source code (not in the test)
- The cmake hook in sdk-native blocks `gradlew build` and cannot be worked around
- Requirements are ambiguous about which tests to keep vs. remove
- Existing bugs in unrelated code affect this feature's tests
