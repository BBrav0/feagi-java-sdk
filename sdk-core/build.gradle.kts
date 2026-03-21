plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    test {
        java.setSrcDirs(listOf("tests"))
    }
}

dependencies {
    // Intentionally minimal for the skeleton.
    implementation("org.zeromq:jeromq:0.6.0")
}

