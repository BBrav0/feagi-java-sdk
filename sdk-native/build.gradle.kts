plugins {
    `java-library`
    `maven-publish`
    signing
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    main {
        resources {
            exclude("native/**")
        }
    }
}

dependencies {
    api(project(":sdk-core"))
}

// --- Platform classifier JARs ---

data class NativePlatform(val classifier: String, val taskName: String)

val nativePlatforms = listOf(
    NativePlatform("linux-x86_64", "nativeLinuxX86_64Jar"),
    NativePlatform("linux-aarch64", "nativeLinuxAarch64Jar"),
    NativePlatform("osx-x86_64", "nativeOsxX86_64Jar"),
    NativePlatform("osx-aarch64", "nativeOsxAarch64Jar"),
    NativePlatform("windows-x86_64", "nativeWindowsX86_64Jar"),
)

val nativeJarTasks = nativePlatforms.associate { platform ->
    platform.classifier to tasks.register<Jar>(platform.taskName) {
        archiveClassifier.set(platform.classifier)
        includeEmptyDirs = false
        from(layout.projectDirectory.dir("src/main/resources/native/${platform.classifier}")) {
            into("native/${platform.classifier}")
        }
    }
}

// --- Sources & Javadoc JARs ---

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier.set("javadoc")
    from(tasks.javadoc.get().destinationDir)
}

// --- Publishing ---

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "sdk-native"
            artifact(sourcesJar)
            artifact(javadocJar)

            nativePlatforms.forEach { platform ->
                artifact(nativeJarTasks[platform.classifier]!!)
            }

            pom {
                name.set("FEAGI Java SDK - Native")
                description.set("JNI-based native bindings for FEAGI Java SDK")
                url.set("https://github.com/feagi/feagi-java-sdk")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("neuraville")
                        name.set("Neuraville Inc.")
                        url.set("https://github.com/feagi/feagi-java-sdk")
                    }
                }

                scm {
                    url.set("https://github.com/feagi/feagi-java-sdk")
                    connection.set("scm:git:https://github.com/feagi/feagi-java-sdk.git")
                    developerConnection.set("scm:git:ssh://git@github.com/feagi/feagi-java-sdk.git")
                }
            }
        }
    }
}

// --- Signing ---

val signingKeyId: String? = (findProperty("signingKeyId") as String?)
    ?: System.getenv("SIGNING_KEY_ID")
val signingKey: String? = (findProperty("signingKey") as String?)
    ?: System.getenv("SIGNING_KEY")
val signingPassword: String? = (findProperty("signingPassword") as String?)
    ?: System.getenv("SIGNING_PASSWORD")
val canSign = !signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()

signing {
    isRequired = canSign
    if (canSign) {
        if (!signingKeyId.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications["mavenJava"])
    }
}

// --- Native CMake build (optional — only when CMake and feagi-java-ffi are available) ---

val nativeBuildDir = layout.buildDirectory.dir("native")
val cmakeSourceDir = file("src/main/cpp")
val feagiFfiDir = rootProject.projectDir.resolve("../feagi-java-ffi")
val feagiFfiIncludeDir = feagiFfiDir.resolve("include")
val feagiFfiLibDir = feagiFfiDir.resolve("target/release")

tasks.register<Exec>("cmakeConfigure") {
    group = "native"
    description = "Configure CMake for JNI bridge"
    onlyIf { cmakeSourceDir.exists() && feagiFfiDir.exists() }
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
    onlyIf { cmakeSourceDir.exists() && feagiFfiDir.exists() }
    dependsOn("cmakeConfigure")

    commandLine(
        "cmake",
        "--build", nativeBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    )
}

// Do NOT hook cmakeBuild into the normal 'build' task — it requires external tooling.
// Instead, users with CMake and feagi-java-ffi can run: ./gradlew :sdk-native:cmakeBuild

tasks.register<JavaExec>("nativeSmokeTest") {
    group = "verification"
    description = "Run ABI smoke test using the built JNI library"
    dependsOn("cmakeBuild", "classes")

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.feagi.sdk.nativeffi.NativeSmokeTest")

    systemProperty("java.library.path", nativeBuildDir.get().asFile.absolutePath)
    environment("PATH", feagiFfiLibDir.absolutePath + ";" + System.getenv("PATH"))

    doFirst {
        val releaseDir = nativeBuildDir.get().asFile.resolve("Release")
        val jniLibDir = if (releaseDir.exists()) releaseDir else nativeBuildDir.get().asFile
        systemProperty("java.library.path", jniLibDir.absolutePath)
        logger.lifecycle("nativeSmokeTest: java.library.path = ${jniLibDir.absolutePath}")
    }
}
