import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("net.kyori.indra")
    id("leafpile.publishing")
}

repositories {
    mavenCentral()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    api(platform(project(":bom")))
    testImplementation(libs.findLibrary("org-junit-jupiter-junit-jupiter").get())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    maxHeapSize = "1G"
    testLogging {
        events("passed")
    }
}

indra {
    javaVersions {
        target(25)
    }
}
