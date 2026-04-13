# Architecture

Architectural decisions, patterns, and conventions discovered during the mission.

---

## Module Dependency Graph
```
sdk-cli → sdk-engine
sdk-native → sdk-core (api dependency, so transitive for consumers)
```

## Native Library Loading Strategy
1. Detect OS + arch → compose platform string (e.g., `osx-aarch64`)
2. Construct resource path: `native/<platform>/<libname>`
3. Try classpath extraction: getResourceAsStream → temp file → System.load(absolutePath)
4. Fallback: System.loadLibrary(libraryName)
5. ABI handshake: call feagiAbiVersion() and compare to EXPECTED_ABI_VERSION

## Maven Artifact Structure
- `org.feagi:sdk-core:0.0.1` — pure Java, no classifiers
- `org.feagi:sdk-native:0.0.1` — base JAR with Java loader code
- `org.feagi:sdk-native:0.0.1:linux-x86_64` — classifier JAR with native lib
- (same pattern for all 5 platforms)
- Classifier JARs are secondary artifacts sharing the base POM

## Platform Naming Convention
| Platform | Classifier | Library Filename |
|----------|-----------|-----------------|
| Linux x86_64 | linux-x86_64 | libfeagi_java_ffi.so |
| Linux aarch64 | linux-aarch64 | libfeagi_java_ffi.so |
| macOS x86_64 | osx-x86_64 | libfeagi_java_ffi.dylib |
| macOS aarch64 | osx-aarch64 | libfeagi_java_ffi.dylib |
| Windows x86_64 | windows-x86_64 | feagi_java_ffi.dll |
