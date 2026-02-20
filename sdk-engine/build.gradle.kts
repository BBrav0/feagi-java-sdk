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
    // TODO: needed once FeagiEngine (Issue 2) references sdk-core types (FeagiSdkException, etc.)
    implementation(project(":sdk-core"))
}
