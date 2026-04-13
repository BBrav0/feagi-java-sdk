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

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

sourceSets {
    test {
        java.setSrcDirs(listOf("tests"))
    }
}

val signingKeyId: String? = (findProperty("signingKeyId") as String?)
    ?: System.getenv("SIGNING_KEY_ID")
val signingKey: String? = (findProperty("signingKey") as String?)
    ?: System.getenv("SIGNING_KEY")
val signingPassword: String? = (findProperty("signingPassword") as String?)
    ?: System.getenv("SIGNING_PASSWORD")
val hasSigningCredentials = !signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()

val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.javadoc)
    archiveClassifier.set("javadoc")
    from(tasks.javadoc.get().destinationDir)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "sdk-core"
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name.set("FEAGI Java SDK - Core")
                description.set("Core API and utilities for the FEAGI Java SDK")
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

signing {
    isRequired = hasSigningCredentials

    if (hasSigningCredentials) {
        if (!signingKeyId.isNullOrBlank()) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
        sign(publishing.publications["mavenJava"])
    }
}

dependencies {
    // Intentionally minimal for the skeleton.
    implementation("org.zeromq:jeromq:0.6.0")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
