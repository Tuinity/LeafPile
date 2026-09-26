plugins {
    id("leafpile.bom")
}

dependencies {
    constraints {
        for (subproject in rootProject.subprojects) {
            if (subproject != project) {
                api(project(subproject.path))
            }
        }
    }
}
