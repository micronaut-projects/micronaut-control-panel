plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Validation, so it does not bring it in
    compileOnly(mnValidation.micronaut.validation)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnValidation.micronaut.validation)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnValidation.micronaut.validation.processor)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
