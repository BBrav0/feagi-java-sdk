plugins {
    java
    application
}

application {
    mainClass.set("io.feagi.sdk.demo.ExampleWebcamMotor")
}

dependencies {
    implementation(project(":sdk-core"))
    implementation(project(":sdk-native"))
    implementation(project(":sdk-engine"))
    implementation("org.bytedeco:javacv-platform:1.5.10")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Fat jar — bundles all dependencies so the demo runs with a single jar
tasks.register<Jar>("demoJar") {
    archiveClassifier.set("demo")
    manifest {
        attributes["Main-Class"] = "io.feagi.sdk.demo.ExampleWebcamMotor"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
