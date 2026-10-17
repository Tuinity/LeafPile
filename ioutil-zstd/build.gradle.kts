plugins {
    id("leafpile.library")
}

dependencies {
    api(project(":ioutil"))
    api(libs.com.github.luben.zstd.jni)
    testImplementation(project(path = ":ioutil", configuration = "testClasses"))
}

tasks.jar {
    manifest {
        attributes("FMLModType" to "GAMELIBRARY")
    }
}
