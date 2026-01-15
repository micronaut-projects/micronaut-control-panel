plugins {
    io.micronaut.build.internal.`control-panel-module`
    io.micronaut.build.internal.`control-panel-ui`
}

dependencies {
    api(projects.micronautControlPanelCore)
    api(mnViews.micronaut.views.handlebars)
    api(libs.handlebars.humanize) {
        version {
            require(mnViews.versions.handlebars.get())
        }
    }
    implementation(mn.micronaut.http.server)
    compileOnly(mn.micronaut.management)


    testImplementation(mn.micronaut.http.client)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnReactor.micronaut.reactor)
    testImplementation(mn.micronaut.management)
    testImplementation(projects.micronautControlPanelManagement)

    // JUnit 5 + Micronaut Test + Mockito
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)

    testRuntimeOnly(mn.snakeyaml)
}
