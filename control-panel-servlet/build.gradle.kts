plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnServlet.servlet.api)
    compileOnly(mnServlet.micronaut.servlet.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnServlet.micronaut.servlet.processor)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.management)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnServlet.micronaut.http.server.jetty)
    testImplementation(mnServlet.micronaut.servlet.api)
    testImplementation(mnServlet.servlet.api)
    testImplementation(projects.micronautControlPanelUi)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
