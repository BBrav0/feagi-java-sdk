rootProject.name = "feagi-java-sdk-examples"

includeBuild("../") {
    dependencySubstitution {
        substitute(module("io.feagi:sdk-core")).using(project(":sdk-core"))
        substitute(module("io.feagi:sdk-engine")).using(project(":sdk-engine"))
        substitute(module("io.feagi:sdk-cli")).using(project(":sdk-cli"))
    }
}

include("minimal-agent")
include("vision-agent")
include("motor-agent")
include("servo-motor")
include("observability")
