---
name: config-worker
description: Handles build configuration, Maven POM, GitHub Actions, and .gitignore changes
---

# Config Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Use for features that involve:
- Maven POM modifications (pom.xml — adding plugins, profiles, classifiers)
- Gradle build script modifications (build.gradle.kts — conditional hooks, JAR tasks)
- GitHub Actions workflow changes (.github/workflows/*.yml)
- .gitignore updates
- README documentation updates
- Any feature where the primary deliverable is configuration/build files

## Required Skills

None.

## Work Procedure

1. **Read the feature description carefully.** Understand what configuration change is needed and why.

2. **Read AGENTS.md** for boundaries, conventions, and constraints. Pay special attention to:
   - Module scope (sdk-core and sdk-native only)
   - Platform classifier naming convention (linux-x86_64, osx-aarch64, etc.)
   - Native library filenames per platform

3. **Read the current file(s) you'll modify.** Understand the full context — don't just insert changes blindly. For POM files, understand the parent-child relationship and inherited plugins.

4. **Plan the change.** For Maven POM changes:
   - Identify which plugin(s) to use (maven-assembly-plugin, maven-jar-plugin, build-helper-maven-plugin)
   - Determine the correct lifecycle phase
   - Plan classifier naming to match the convention in AGENTS.md
   
   For GitHub Actions:
   - Understand the existing workflow structure
   - Plan new steps with correct ordering and dependencies
   - Plan `if` conditions for conditional steps

5. **Implement the change.**
   - For XML files (POM): maintain consistent indentation (2 or 4 spaces matching existing file)
   - For YAML files (workflows): use 2-space indentation, quote strings with special chars
   - For Gradle Kotlin DSL: use Kotlin syntax, match existing patterns

6. **Validate the change.**
   - For Gradle changes: run `cd /d C:\Users\bendc\Documents\GitHub\feagi-java-sdk && gradlew.bat build` (or appropriate subset)
   - For POM changes: structural validation only (Maven is not installed). Verify XML is well-formed. Check that artifactId, groupId, version are correct.
   - For workflow changes: verify YAML syntax is valid. Check that secret references use `${{ secrets.* }}` syntax.
   - For .gitignore changes: verify with `git status` that intended files are tracked/untracked.
   - For JAR content changes: use `jar tf <path>` to verify contents.

7. **Cross-reference with AGENTS.md.** Verify:
   - No boundary violations (didn't modify sdk-engine or sdk-cli source)
   - Correct classifier names used
   - Correct native library filenames used
   - Version is `0.0.1`

## Example Handoff

```json
{
  "salientSummary": "Added maven-assembly-plugin configuration to sdk-native/pom.xml with 5 assembly descriptor executions producing platform-classified JARs. Each descriptor includes only the native lib for that platform. Verified XML is well-formed and classifier names match convention.",
  "whatWasImplemented": "Created 5 Maven assembly descriptors at sdk-native/src/assembly/{linux-x86_64,linux-aarch64,osx-x86_64,osx-aarch64,windows-x86_64}.xml. Each descriptor produces a JAR with classifier matching its platform, including only native/<platform>/ directory contents. Added maven-assembly-plugin to sdk-native/pom.xml with 5 executions referencing the descriptors, bound to package phase.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {
        "command": "python3 -c \"import xml.etree.ElementTree as ET; ET.parse('sdk-native/pom.xml'); print('Valid XML')\"",
        "exitCode": 0,
        "observation": "POM is valid XML"
      },
      {
        "command": "jar tf sdk-native/target/sdk-native-0.0.1-linux-x86_64.jar",
        "exitCode": 0,
        "observation": "Contains META-INF/MANIFEST.MF and native/linux-x86_64/libfeagi_java_ffi.so only"
      }
    ],
    "interactiveChecks": [
      {
        "action": "Inspected each assembly descriptor XML for correct include/exclude patterns",
        "observed": "Each descriptor includes only its platform directory and excludes all others"
      }
    ]
  },
  "tests": {
    "added": []
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Maven POM change requires a feature that isn't yet implemented (e.g., assembly descriptors reference files that don't exist yet)
- GitHub Actions workflow change requires secrets that haven't been configured
- Build configuration change breaks compilation in an unexpected way
- The change would violate a boundary (e.g., need to modify sdk-engine POM)
- Ambiguity about which Maven plugin to use or how to configure classifier JARs
