plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnCache.micronaut.cache.core)
}

micronautBuild {
    binaryCompatibility.enabledAfter("1.10.0")
}
