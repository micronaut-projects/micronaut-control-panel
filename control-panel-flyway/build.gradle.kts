plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnFlyway.flyway)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnFlyway.flyway)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
