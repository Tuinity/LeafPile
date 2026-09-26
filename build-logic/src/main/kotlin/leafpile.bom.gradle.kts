plugins {
    `java-platform`
    id("leafpile.publishing")
}

indra {
    configurePublications {
        from(components["javaPlatform"])
    }
}
