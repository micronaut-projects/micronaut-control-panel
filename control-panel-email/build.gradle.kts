plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Email, so it does not bring it in
    compileOnly(mnEmail.micronaut.email)
    compileOnly(mnEmail.micronaut.email.javamail)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnEmail.micronaut.email.javamail)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
