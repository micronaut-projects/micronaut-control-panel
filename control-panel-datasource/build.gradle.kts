import io.micronaut.testresources.buildtools.KnownModules

plugins {
    io.micronaut.build.internal.`control-panel-module`
    io.micronaut.`test-resources`
}

micronaut {
    version = libs.versions.micronaut.platform.get()
    testResources {
        enabled = true
        clientTimeout = 300
        version = libs.versions.micronaut.testresources.get()
        additionalModules.add(KnownModules.JDBC_ORACLE_FREE)
        additionalModules.add(KnownModules.JDBC_POSTGRESQL)
    }
}

dependencies {
    api(projects.micronautControlPanelCore)
    api(mnViews.micronaut.views.core)
    annotationProcessor(mnSerde.micronaut.serde.processor)

    implementation(mnSql.micronaut.jdbc)
    implementation(mnData.micronaut.data.connection.jdbc)
    implementation(mnSerde.micronaut.serde.api)

    compileOnly(mnSql.micronaut.jdbc.hikari)
    compileOnly(mnSql.micronaut.jdbc.ucp)

    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mn.micronaut.http.client)
    testImplementation(mnViews.micronaut.views.handlebars)
    testImplementation(mnSql.micronaut.jdbc.hikari)
    testImplementation(mnSql.micronaut.jdbc.ucp)
    testImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.bytebuddy.agent)
    testRuntimeOnly(mnSql.ojdbc11)
    testRuntimeOnly(mnSql.postgresql)
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}

// Verifies the datasource panel works when micronaut-data-connection-jdbc is absent from the
// classpath, as it is for applications that do not use Micronaut Data. The standard test task has
// the module present; this task strips it to reproduce the NoClassDefFoundError that occurred while
// DataSourceService referenced DelegatingDataSource directly and the module was an `implementation`
// dependency.
val testWithoutDataConnection by tasks.registering(Test::class) {
    description = "Verifies the datasource panel instantiates when micronaut-data-connection-jdbc is absent"
    group = "verification"
    classpath = configurations.named("testRuntimeClasspath").get()
        .filter { !it.name.contains("micronaut-data-connection") }
        .plus(sourceSets.main.get().output)
        .plus(sourceSets.test.get().output)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    filter {
        includeTestsMatching("io.micronaut.controlpanel.panels.datasource.DataConnectionJdbcAbsentFromClasspathTest")
    }
}

tasks.named("check") {
    dependsOn(testWithoutDataConnection)
}
