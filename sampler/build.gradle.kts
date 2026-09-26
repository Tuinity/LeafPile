plugins {
    id("leafpile.library")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":concurrentutil"))
    implementation(project(":ioutil"))
    api(libs.it.unimi.dsi.fastutil)
    implementation(libs.org.slf4j.slf4j.api)
    implementation(libs.com.github.luben.zstd.jni)
}
