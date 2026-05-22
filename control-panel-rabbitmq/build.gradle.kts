plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnReactor.micronaut.reactor)

    compileOnly(mnRabbitmq.micronaut.rabbitmq)
    compileOnly(mnMicrometer.micronaut.micrometer.core)
    compileOnly(mn.micronaut.management)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnRabbitmq.micronaut.rabbitmq)
    testImplementation(mnMicrometer.micronaut.micrometer.core)
    testImplementation(mn.micronaut.management)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
