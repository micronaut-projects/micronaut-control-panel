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
    annotationProcessor(mnSerde.micronaut.serde.processor)

    implementation(mnSql.micronaut.jdbc)
    implementation(mnData.micronaut.data.connection.jdbc)
    implementation(mnSerde.micronaut.serde.api)

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.bytebuddy.agent)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.1.0")
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}
