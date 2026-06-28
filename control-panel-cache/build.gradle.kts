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
    testRuntimeOnly(mn.snakeyaml)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)

    testImplementation(mnCache.micronaut.cache.caffeine)
    testImplementation(mnCache.micronaut.cache.ehcache)
    testImplementation(mnCache.micronaut.cache.hazelcast)
    testImplementation(mnCache.micronaut.cache.infinispan)
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}

// Verifies that the context starts cleanly when Ehcache is absent from the classpath.
// The standard test task has all cache implementations on the classpath; this task strips
// Ehcache JARs to reproduce the NoClassDefFoundError that occurs without the
// @Requires(classes = EhcacheSyncCache.class) guard on EhcacheControlPanel.
val testWithoutEhcache by tasks.registering(Test::class) {
    description = "Verifies the control panel repository is queryable when Ehcache is absent from the classpath"
    group = "verification"
    classpath = configurations.named("testRuntimeClasspath").get()
        .filter { !it.name.contains("ehcache") }
        .plus(sourceSets.main.get().output)
        .plus(sourceSets.test.get().output)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    filter {
        includeTestsMatching("io.micronaut.controlpanel.panels.cache.EhcacheAbsentFromClasspathTest")
    }
}

tasks.named("check") {
    dependsOn(testWithoutEhcache)
}

