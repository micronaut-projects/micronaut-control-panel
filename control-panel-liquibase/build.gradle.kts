plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    // The panel only makes sense in applications that already use Micronaut Liquibase, so it does not bring it in
    compileOnly(mnLiquibase.micronaut.liquibase)
    compileOnly(mnSql.micronaut.jdbc)

    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnLiquibase.micronaut.liquibase)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testRuntimeOnly(mnSql.h2)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.3.0")
}
