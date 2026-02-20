plugins {
    // Root project: no applied plugins by default.
}

allprojects {
    group = "io.feagi"
    version = "0.0.1-beta.0"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withId("java-library") {
        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

