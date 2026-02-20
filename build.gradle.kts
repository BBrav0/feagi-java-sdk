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
            "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
            "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.10.2")
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

