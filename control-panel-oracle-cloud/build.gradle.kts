plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Oracle Cloud, so it does not bring it in
    compileOnly(mnOraclecloud.micronaut.oraclecloud.common)
    compileOnly(mn.micronaut.http.client.core)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnOraclecloud.micronaut.oraclecloud.bmc.objectstorage)
    testImplementation(mnOraclecloud.micronaut.oraclecloud.httpclient.netty)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
