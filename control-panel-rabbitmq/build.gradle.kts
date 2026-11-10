import io.micronaut.testresources.buildtools.KnownModules

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
        additionalModules.add(KnownModules.RABBITMQ)
    }
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut RabbitMQ, so it does not bring it in
    compileOnly(mnRabbitmq.micronaut.rabbitmq)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnRabbitmq.micronaut.rabbitmq)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
