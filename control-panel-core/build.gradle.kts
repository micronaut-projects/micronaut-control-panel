plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(mn.micronaut.router)
    implementation(mn.micronaut.discovery.core)
    implementation(mn.micronaut.http.server)
    implementation(mn.micronaut.json.core)

    compileOnly(mnTestResources.micronaut.test.resources.client)

    testImplementation(mn.micronaut.http)
    testImplementation(mn.micronaut.management)
    testImplementation(projects.micronautControlPanelManagement)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.junit.jupiter.params)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.1.0")
}
