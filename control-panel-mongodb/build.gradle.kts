plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnMongo.micronaut.mongo.core)
    compileOnly(mnMongo.micronaut.mongo.sync)
    compileOnly(mnMongo.micronaut.mongo.reactive)
    implementation(mnReactor.micronaut.reactor)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnMongo.micronaut.mongo.core)
    testImplementation(mnMongo.micronaut.mongo.sync)
    testImplementation(mnMongo.micronaut.mongo.reactive)
    testImplementation(libs.testcontainers.mongodb)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
