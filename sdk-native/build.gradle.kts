plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(project(":sdk-core"))
}

val nativeBuildDir = layout.buildDirectory.dir("native")
val cmakeSourceDir = file("src/main/cpp")

// Layout: feagi/feagi-java-sdk/  and  feagi/feagi-java-ffi/
val feagiFfiDir        = rootProject.projectDir.resolve("../feagi-java-ffi")
val feagiFfiIncludeDir = feagiFfiDir.resolve("include")
val feagiFfiLibDir     = feagiFfiDir.resolve("target/release")

tasks.register<Exec>("cmakeConfigure") {
    group = "native"
    description = "Configure CMake for JNI bridge"
    doFirst { nativeBuildDir.get().asFile.mkdirs() }

    commandLine(
        "cmake",
        "-S", cmakeSourceDir.absolutePath,
        "-B", nativeBuildDir.get().asFile.absolutePath,
        "-DFEAGI_FFI_INCLUDE_DIR=${feagiFfiIncludeDir.absolutePath}",
        "-DFEAGI_FFI_LIB_DIR=${feagiFfiLibDir.absolutePath}"
    )
}

tasks.register<Exec>("cmakeBuild") {
    group = "native"
    description = "Build JNI bridge"
    dependsOn("cmakeConfigure")

    commandLine(
        "cmake",
        "--build", nativeBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    )
}

// Hook into normal build
tasks.named("build") { dependsOn("cmakeBuild") }

// Smoke test runner
tasks.register<JavaExec>("nativeSmokeTest") {
    group = "verification"
    description = "Run ABI smoke test using the built JNI library"
    dependsOn("cmakeBuild", "classes")

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.feagi.sdk.nativeffi.NativeSmokeTest")

    // Evaluated at configuration time — just set a placeholder; doFirst overrides it.
    systemProperty("java.library.path", nativeBuildDir.get().asFile.absolutePath)

    // Windows: ensure Rust DLL is discoverable at runtime
    environment("PATH", feagiFfiLibDir.absolutePath + ";" + System.getenv("PATH"))

    // doFirst runs AFTER cmakeBuild, so Release/ now exists on Windows.
    doFirst {
        val releaseDir = nativeBuildDir.get().asFile.resolve("Release")
        val jniLibDir = if (releaseDir.exists()) releaseDir else nativeBuildDir.get().asFile
        systemProperty("java.library.path", jniLibDir.absolutePath)
        logger.lifecycle("nativeSmokeTest: java.library.path = ${jniLibDir.absolutePath}")
    }
}