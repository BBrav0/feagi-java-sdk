plugins {
    // Root project: no applied plugins by default.
}

val ossrhUsername: String? = (findProperty("ossrhUsername") as String?)
    ?: System.getenv("OSSRH_USERNAME")
val ossrhPassword: String? = (findProperty("ossrhPassword") as String?)
    ?: System.getenv("OSSRH_TOKEN")

allprojects {
    group = "org.feagi"
    version = "0.0.1"
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

    plugins.withId("maven-publish") {
        extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "sonatype"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    if (!ossrhUsername.isNullOrBlank() && !ossrhPassword.isNullOrBlank()) {
                        credentials {
                            username = ossrhUsername
                            password = ossrhPassword
                        }
                    }
                }
            }
        }
    }
}
