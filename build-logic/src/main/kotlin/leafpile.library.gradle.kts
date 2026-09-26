import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("net.kyori.indra")
    id("net.kyori.indra.publishing")
}

repositories {
    mavenCentral()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
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
    publishSnapshotsTo("paperSnapshots", "https://repo.papermc.io/repository/maven-snapshots/")
    publishReleasesTo("paperReleases", "https://repo.papermc.io/repository/maven-releases/")
    gpl3OnlyLicense()
    github("Tuinity", "LeafPile")
    configurePublications {
        pom {
            developers {
                developer {
                    id = "spottedleaf"
                }
            }
        }
    }
    signWithKeyFromProperties("signingKey", "signingPassword")
}
