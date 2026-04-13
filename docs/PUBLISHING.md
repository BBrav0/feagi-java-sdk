# FEAGI Java SDK — Maven Dependencies

## Maven

### sdk-core

The core module contains public API types (configuration, capabilities, enums, interfaces) with no native dependencies.

```xml
<dependency>
    <groupId>org.feagi</groupId>
    <artifactId>sdk-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

### sdk-native

The native module provides JNI bindings and the native library loader. It depends on `sdk-core` transitively.

**Base JAR** (Java code — required):

```xml
<dependency>
    <groupId>org.feagi</groupId>
    <artifactId>sdk-native</artifactId>
    <version>0.0.1</version>
</dependency>
```

**Platform classifier JAR** (native library — pick your platform):

```xml
<dependency>
    <groupId>org.feagi</groupId>
    <artifactId>sdk-native</artifactId>
    <version>0.0.1</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

> **Note:** You need **both** the base `sdk-native` JAR (Java code) **and** the platform classifier JAR (native library) on your classpath.

## Gradle (Kotlin DSL)

```kotlin
implementation("org.feagi:sdk-core:0.0.1")
implementation("org.feagi:sdk-native:0.0.1")
runtimeOnly("org.feagi:sdk-native:0.0.1:linux-x86_64")
```

> **Note:** Consumers need **both** the base `sdk-native` JAR (Java code) **and** the platform classifier JAR (native lib).

## Platform Classifiers

| Platform | Classifier | Native Library Filename |
|----------|-----------|------------------------|
| Linux x86_64 | `linux-x86_64` | `libfeagi_java_ffi.so` |
| Linux aarch64 | `linux-aarch64` | `libfeagi_java_ffi.so` |
| macOS x86_64 | `osx-x86_64` | `libfeagi_java_ffi.dylib` |
| macOS aarch64 | `osx-aarch64` | `libfeagi_java_ffi.dylib` |
| Windows x86_64 | `windows-x86_64` | `feagi_java_ffi.dll` |

> **Note:** macOS classifiers use the `osx` prefix (not `darwin`).

## Minimal Consumer Example

```java
import io.feagi.sdk.nativeffi.FeagiNativeLibrary;

public class Main {
    public static void main(String[] args) {
        FeagiNativeLibrary.loadAndVerify("feagi_java_ffi");
        System.out.println("FEAGI native library loaded successfully!");
    }
}
```

The loader automatically detects the platform, extracts the native library from the classpath, and loads it. If the platform classifier JAR is not on the classpath, it falls back to `System.loadLibrary` (requires the native lib on `java.library.path`).
