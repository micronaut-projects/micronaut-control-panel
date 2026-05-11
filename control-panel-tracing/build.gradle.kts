plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mnViews.micronaut.views.handlebars)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
