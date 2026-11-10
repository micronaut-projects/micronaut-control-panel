plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut JMX, so it does not bring it in
    compileOnly(libs.micronaut.jmx)
    compileOnly(mn.micronaut.management)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.micronaut.jmx)
    testImplementation(mn.micronaut.management)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
