plugins {
    io.micronaut.build.internal.`control-panel-module`
    io.micronaut.`test-resources`
}

micronaut {
    version = libs.versions.micronaut.platform.get()
    testResources {
        enabled = true
        clientTimeout = 300
        version = libs.versions.micronaut.testresources.get()
    }
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnCache.micronaut.cache.core)
    implementation(mnReactor.micronaut.reactor)

    compileOnly(mnCache.micronaut.cache.caffeine)
    compileOnly(mnCache.micronaut.cache.ehcache)
    compileOnly(mnCache.micronaut.cache.hazelcast)
    compileOnly(mnCache.micronaut.cache.infinispan)

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)

    testImplementation(mnCache.micronaut.cache.caffeine)
    testImplementation(mnCache.micronaut.cache.ehcache)
    testImplementation(mnCache.micronaut.cache.hazelcast)
    testImplementation(mnCache.micronaut.cache.infinispan)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
