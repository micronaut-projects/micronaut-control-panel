plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    annotationProcessor(mnSerde.micronaut.serde.processor)

    api(projects.micronautControlPanelCore)
    implementation(mnReactor.micronaut.reactor)
    implementation(mn.micronaut.http.server)
    implementation(mnSerde.micronaut.serde.api)

    compileOnly(mnEmail.micronaut.email)

    testImplementation(mnEmail.micronaut.email)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
