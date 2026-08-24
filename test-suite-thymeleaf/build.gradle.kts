plugins {
    java
}
dependencies {
    testAnnotationProcessor(mn.micronaut.inject.java)
    testRuntimeOnly(mnLogging.logback.classic)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mn.micronaut.jackson.databind)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testRuntimeOnly(mnTest.junit.platform.suite)
    testImplementation(mnViews.micronaut.views.thymeleaf)
    testImplementation(projects.micronautControlPanelUi)
    testImplementation(projects.micronautControlPanelManagement)
}
tasks.withType<Test> {
    useJUnitPlatform()
}
