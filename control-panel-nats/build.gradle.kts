plugins {
    io.micronaut.build.internal.`control-panel-module`
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.1.0")
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(platform(libs.micronaut.nats))
    compileOnly("io.micronaut.nats:micronaut-nats")

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(platform(libs.micronaut.nats))
    testImplementation("io.micronaut.nats:micronaut-nats")
}
