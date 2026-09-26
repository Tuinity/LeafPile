plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("net.kyori.indra:net.kyori.indra.gradle.plugin:4.1.0")
    implementation("net.kyori.indra.publishing:net.kyori.indra.publishing.gradle.plugin:4.1.0")
}
