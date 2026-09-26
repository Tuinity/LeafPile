plugins {
    id("leafpile.library")
}

dependencies {
    api(project(":common"))
    api(libs.org.slf4j.slf4j.api)
    api(libs.org.yaml.snakeyaml)
}
