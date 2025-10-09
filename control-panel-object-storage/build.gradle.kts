plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnObjectStorage.micronaut.`object`.storage.core)
    implementation(mn.micronaut.http.server)

    compileOnly(mnObjectStorage.micronaut.`object`.storage.aws)
    compileOnly(mnObjectStorage.micronaut.`object`.storage.azure)
    compileOnly(mnObjectStorage.micronaut.`object`.storage.gcp)
    compileOnly(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)
    compileOnly(mnObjectStorage.micronaut.`object`.storage.local)

    testImplementation(mnObjectStorage.micronaut.`object`.storage.local)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.aws)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.azure)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.gcp)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)
}

micronautBuild {
    binaryCompatibility.enabledAfter("1.10.0")
}
