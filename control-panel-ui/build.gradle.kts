plugins {
    io.micronaut.build.internal.`control-panel-module`
    io.micronaut.build.internal.`control-panel-ui`
}

dependencies {
    api(projects.micronautControlPanelCore)
    // The Control Panel uses Java helpers and ordinary Handlebars compilation; Nashorn is only
    // required by Handlebars' optional JavaScript helper/precompilation APIs.
    implementation(mnViews.handlebars) {
        exclude(group = "org.openjdk.nashorn", module = "nashorn-core")
    }
    implementation(mn.micronaut.http.server)
    compileOnly(mn.micronaut.management)


    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnReactor.micronaut.reactor)
    testImplementation(mn.micronaut.management)
    testImplementation(mnCache.micronaut.cache.caffeine)
    testImplementation(mnCache.micronaut.cache.ehcache)
    testImplementation(mnCache.micronaut.cache.hazelcast)
    testImplementation(mnCache.micronaut.cache.infinispan)
    testImplementation(libs.micronaut.security)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.aws)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.azure)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.gcp)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.local)
    testImplementation(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testImplementation(projects.micronautControlPanelCache)
    testImplementation(projects.micronautControlPanelDatasource)
    testImplementation(projects.micronautControlPanelManagement)
    testImplementation(projects.micronautControlPanelObjectStorage)

    // JUnit 5 + Micronaut Test + Mockito
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnSql.h2)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)

    testRuntimeOnly(mn.snakeyaml)
}
