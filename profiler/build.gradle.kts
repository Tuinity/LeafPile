plugins {
    id("leafpile.library")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.it.unimi.dsi.fastutil)
    implementation(libs.org.slf4j.slf4j.api)
}
