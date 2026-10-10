plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // Only the inspector API is needed to compile; applications add the inspector themselves
    compileOnly(libs.micronaut.tracing.opentelemetry.inspector) {
        isTransitive = false
    }

    testImplementation(libs.micronaut.tracing.opentelemetry.inspector)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mn.snakeyaml)
    testRuntimeOnly(mn.micronaut.management)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

// Micronaut Tracing 8.4.0-SNAPSHOT is built against Micronaut Core 5.3.0-SNAPSHOT. Keep the tests on the Micronaut Core
// version of this build. Remove once the build moves to Micronaut Tracing 8.4.0 and the matching Micronaut Core.
configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.micronaut" && requested.version?.startsWith("5.3.") == true) {
            useVersion(libs.versions.micronaut.asProvider().get())
        }
    }
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
