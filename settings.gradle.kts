rootProject.name = "leafpile"

pluginManagement {
    includeBuild("build-logic")
}

include(
    "common",
    "converter",
    "concurrentutil",
    "ioutil",
    "profiler",
    "sampler",
    "yamlconfig",
)

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
