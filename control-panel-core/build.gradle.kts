plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(mn.micronaut.router)
    implementation(mn.micronaut.discovery.core)
    implementation(mn.micronaut.http.server)
    implementation(mn.micronaut.json.core)
    compileOnly(libs.micronaut.security)

    compileOnly(mnTestResources.micronaut.test.resources.client)

    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.management)
    testImplementation(mnReactor.micronaut.reactor)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(libs.micronaut.security)
    testImplementation(projects.micronautControlPanelManagement)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.params)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}
