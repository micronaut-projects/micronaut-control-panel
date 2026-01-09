import io.micronaut.testresources.buildtools.KnownModules

plugins {
    io.micronaut.build.internal.`control-panel-example`
}

dependencies {
    annotationProcessor(mnSerde.micronaut.serde.processor)
    annotationProcessor(mnData.micronaut.data.processor)
    implementation(mnSerde.micronaut.serde.jackson)

    runtimeOnly(projects.micronautControlPanelUi)
    implementation(projects.micronautControlPanelManagement)
    implementation(mn.micronaut.management)

    // Object Storage
    implementation(projects.micronautControlPanelObjectStorage)
    implementation(mnObjectStorage.micronaut.`object`.storage.local)
    implementation(mnObjectStorage.micronaut.`object`.storage.aws)
    implementation(mnObjectStorage.micronaut.`object`.storage.azure)
    implementation(mnObjectStorage.micronaut.`object`.storage.gcp)
    implementation(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)

    // Cache
    implementation(projects.micronautControlPanelCache)
    runtimeOnly(mnCache.micronaut.cache.management)
    implementation(mnCache.micronaut.cache.caffeine)
    implementation(mnCache.micronaut.cache.ehcache)
    implementation(mnCache.micronaut.cache.hazelcast)
    implementation(mnCache.micronaut.cache.infinispan)

    //Datasource
    implementation(projects.micronautControlPanelDatasource)
    implementation(mnSql.micronaut.jdbc.hikari)
    implementation(mnData.micronaut.data.jdbc)
    runtimeOnly(mnSql.ojdbc11)
    runtimeOnly(mnSql.postgresql)

    runtimeOnly(mnLogging.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.playwright)
    testRuntimeOnly(mnTest.junit.platform.suite)
}

micronaut {
    testResources {
        additionalModules.add(KnownModules.CONTROL_PANEL)
        additionalModules.add(KnownModules.JDBC_ORACLE_FREE)
        additionalModules.add(KnownModules.JDBC_POSTGRESQL)
    }
}
