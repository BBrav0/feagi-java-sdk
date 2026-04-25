plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

sourceSets {
    test {
        java.setSrcDirs(listOf("tests"))
    }
}

dependencies {
    // Intentionally minimal for the skeleton.
    implementation("org.zeromq:jeromq:0.6.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

