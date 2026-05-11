plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnServlet.servlet.api)
    compileOnly(mnServlet.micronaut.servlet.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnServlet.servlet.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
