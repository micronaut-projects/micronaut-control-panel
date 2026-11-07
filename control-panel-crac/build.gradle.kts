plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut CRaC, so it does not bring it in
    compileOnly(mnCrac.micronaut.crac)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnCrac.micronaut.crac)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
