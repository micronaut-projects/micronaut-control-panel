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
    implementation(mnReactor.micronaut.reactor)

    compileOnly(mnKafka.micronaut.kafka)
    compileOnly(mnKafka.micronaut.kafka.streams)
    compileOnly(mn.micronaut.management)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnKafka.micronaut.kafka)
    testImplementation(mnKafka.micronaut.kafka.streams)
    testImplementation(mn.micronaut.management)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}
