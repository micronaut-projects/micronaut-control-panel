plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnReactor.micronaut.reactor)

    compileOnly(mnReactor.micronaut.reactor.http.client)
    compileOnly(mnReactor.micrometer.context.propagation)
    compileOnly(mnMicrometer.micrometer.core)

    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnReactor.micronaut.reactor.http.client)
    testImplementation(mnReactor.micrometer.context.propagation)
    testImplementation(mnMicrometer.micrometer.core)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
