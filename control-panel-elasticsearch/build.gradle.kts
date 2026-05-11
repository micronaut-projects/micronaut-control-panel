plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnElasticsearch.micronaut.elasticsearch)
    compileOnly(mnElasticsearch.elasticsearch.rest.client)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnElasticsearch.micronaut.elasticsearch)
    testImplementation(mnElasticsearch.elasticsearch.rest.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.bytebuddy.agent)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
