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

    // The panel only makes sense in applications that already use Micronaut Elasticsearch, so it does not bring it in
    compileOnly(mnElasticsearch.micronaut.elasticsearch)
    compileOnly(mnElasticsearch.elasticsearch.rest.client)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mnElasticsearch.micronaut.elasticsearch)
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
