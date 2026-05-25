plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    implementation(mnR2dbc.micronaut.r2dbc.core)
    implementation(mnReactor.micronaut.reactor)

    compileOnly(mnR2dbc.r2dbc.pool)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnR2dbc.r2dbc.pool)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
