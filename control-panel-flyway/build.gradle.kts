plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Flyway, so it does not bring it in
    compileOnly(mnFlyway.micronaut.flyway)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnFlyway.micronaut.flyway)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testRuntimeOnly(mnSql.h2)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
