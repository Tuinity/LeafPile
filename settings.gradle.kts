rootProject.name = "leafpile"

pluginManagement {
    includeBuild("build-logic")
}

include(
    "bom",
    "common",
    "converter",
    "concurrentutil",
    "ioutil",
    "ioutil-zstd",
    "profiler",
    "sampler",
    "yamlconfig",
)

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
