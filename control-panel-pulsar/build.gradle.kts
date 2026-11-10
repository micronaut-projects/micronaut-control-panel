plugins {
    io.micronaut.build.internal.`control-panel-module`
    io.micronaut.`test-resources`
}

micronaut {
    version = libs.versions.micronaut.platform.get()
    testResources {
        enabled = true
        clientTimeout = 300
        version = libs.versions.micronaut.testresources.get()
    }
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Pulsar, so it does not bring it in
    compileOnly(mnPulsar.micronaut.pulsar)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnPulsar.micronaut.pulsar)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mn.snakeyaml)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
