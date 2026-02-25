plugins {
    `java-library`
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":sdk-engine"))
    implementation("info.picocli:picocli:4.7.6")
    implementation("org.tomlj:tomlj:1.1.1")
}

application {
    mainClass.set("io.feagi.sdk.cli.FeagiCli")
}

sourceSets {
    test {
        java.setSrcDirs(listOf("tests"))
    }
}
