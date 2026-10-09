plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    annotationProcessor(mnSerde.micronaut.serde.processor)
    implementation(mnSerde.micronaut.serde.api)

    compileOnly(mnLangchain4j.micronaut.langchain4j.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnLangchain4j.micronaut.langchain4j.processor)
    testAnnotationProcessor(mnSerde.micronaut.serde.processor)
    testImplementation(mnLangchain4j.micronaut.langchain4j.core)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mn.micronaut.management)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    // a new module: there is no previous release to compare with
    binaryCompatibility.enabled = false
}
