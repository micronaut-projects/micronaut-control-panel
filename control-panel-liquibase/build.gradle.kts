plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnLiquibase.micronaut.liquibase)
    compileOnly(mnSql.micronaut.jdbc)
    compileOnly(mnLiquibase.liquibase)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnViews.handlebars)
    testImplementation(mnLiquibase.micronaut.liquibase)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testImplementation(mnLiquibase.liquibase)

    testAnnotationProcessor(mn.micronaut.inject.java)

    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(mnSql.h2)
}

micronautBuild {
    // This module has no released artifact yet, so there is no baseline to compare against.
    // It ships in 2.3.0 at the earliest.
    binaryCompatibility.enabledAfter("2.3.0")
}
