plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mn.micronaut.management)
    implementation(mnReactor.micronaut.reactor)

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.1.0")
}
