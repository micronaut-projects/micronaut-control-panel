plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(platform(libs.micronaut.validation))
    compileOnly("io.micronaut.validation:micronaut-validation")

    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(platform(libs.micronaut.validation))
    testAnnotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(platform(libs.micronaut.validation))
    testImplementation("io.micronaut.validation:micronaut-validation")
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
