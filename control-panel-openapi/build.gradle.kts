plugins {
    id("io.micronaut.build.internal.control-panel-module")
}

description = "Micronaut Control Panel - OpenAPI integration"

dependencies {
    api(projects.micronautControlPanelCore)
    // No direct dependency on micronaut-openapi; we only read properties exposed by it.
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}
