plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Views, so it does not bring it in
    compileOnly(mnViews.micronaut.views.core)
    compileOnly(mn.micronaut.router)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnViews.micronaut.views.thymeleaf)
    testImplementation(mn.micronaut.http.server)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
