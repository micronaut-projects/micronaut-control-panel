plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut R2DBC, so it does not bring it in
    compileOnly(mnR2dbc.micronaut.r2dbc.core)
    compileOnly(mnR2dbc.r2dbc.pool)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnR2dbc.micronaut.r2dbc.core)
    testImplementation(mnR2dbc.r2dbc.pool)
    testRuntimeOnly(mnR2dbc.r2dbc.h2)
    testImplementation(mnReactor.micronaut.reactor)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
