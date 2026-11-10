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

    // The panel only makes sense in applications that already use Micronaut OpenSearch, so it does not bring it in.
    // Both transports are supported.
    compileOnly(mnOpensearch.micronaut.opensearch)
    compileOnly(mnOpensearch.micronaut.opensearch.httpclient5)
    compileOnly(mnOpensearch.micronaut.opensearch.restclient)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mnOpensearch.micronaut.opensearch.httpclient5)
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
