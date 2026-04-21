plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    annotationProcessor(mnSerde.micronaut.serde.processor)
    implementation(mn.micronaut.management)
    implementation(mnMicrometer.micronaut.micrometer.core)
    implementation(mnReactor.micronaut.reactor)
    implementation(mnSerde.micronaut.serde.api)

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}
