plugins {
    id("leafpile.library")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":concurrentutil"))
    api(libs.com.github.luben.zstd.jni)
}

// Consumers may use VarHandles and foreign-memory APIs with Minecraft, mod, or
// other GAMELIBRARY classes. NeoForge must load this JAR as a game library for
// that access to work.
tasks.jar {
    manifest {
        attributes("FMLModType" to "GAMELIBRARY")
    }
}
