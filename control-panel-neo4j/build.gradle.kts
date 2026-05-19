plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnNeo4j.neo4j.java.driver)
    compileOnly(mnNeo4j.micronaut.neo4j.bolt)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnNeo4j.neo4j.java.driver)
    testImplementation(mnNeo4j.micronaut.neo4j.bolt)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnViews.micronaut.views.handlebars)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnTest.bytebuddy.agent)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
