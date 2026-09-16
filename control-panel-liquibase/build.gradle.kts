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
    // Keep this aligned with the current project version until the first release is published.
    binaryCompatibility.enabledAfter("2.1.1")
}
