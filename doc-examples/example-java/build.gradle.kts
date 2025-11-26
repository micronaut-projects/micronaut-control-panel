plugins {
    io.micronaut.build.internal.`control-panel-example`
}

dependencies {
    annotationProcessor(mnSerde.micronaut.serde.processor)
    implementation(mnSerde.micronaut.serde.jackson)

    runtimeOnly(projects.micronautControlPanelUi)
    implementation(projects.micronautControlPanelManagement)
    implementation(mn.micronaut.management)

    implementation(projects.micronautControlPanelObjectStorage)
    implementation(mnObjectStorage.micronaut.`object`.storage.local)
    implementation(mnObjectStorage.micronaut.`object`.storage.aws)
    implementation(mnObjectStorage.micronaut.`object`.storage.azure)
    implementation(mnObjectStorage.micronaut.`object`.storage.gcp)
    implementation(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)

    implementation(projects.micronautControlPanelCache)
    runtimeOnly(mnCache.micronaut.cache.management)
    implementation(mnCache.micronaut.cache.caffeine)
    implementation(mnCache.micronaut.cache.ehcache)
    implementation(mnCache.micronaut.cache.hazelcast)
    implementation(mnCache.micronaut.cache.infinispan)

    runtimeOnly(mnLogging.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.playwright)
    testRuntimeOnly(mnTest.junit.platform.suite)
}
