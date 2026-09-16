plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mn.micronaut.context.python)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mn.micronaut.context.python)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnViews.micronaut.views.handlebars) {
        exclude(group = "org.openjdk.nashorn", module = "nashorn-core")
    }
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.2.0")
}

// GraalPyRuntimeIntegrationTest boots a real polyglot engine and context, so it stays opt-in:
// ./gradlew :micronaut-control-panel-graalpy:test -Dgraalpy.integration=true --tests '*GraalPyRuntimeIntegrationTest*'
tasks.withType<Test>().configureEach {
    systemProperty("graalpy.integration", System.getProperty("graalpy.integration") ?: "false")
}
