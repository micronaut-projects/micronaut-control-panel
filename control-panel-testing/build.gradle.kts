plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.management)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    // a new module: there is no previous release to compare with
    binaryCompatibility.enabled = false
}
