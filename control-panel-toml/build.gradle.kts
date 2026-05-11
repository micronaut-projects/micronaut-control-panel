plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnToml.micronaut.toml)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnToml.micronaut.toml)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
