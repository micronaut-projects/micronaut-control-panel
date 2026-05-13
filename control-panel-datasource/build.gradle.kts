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
    testResourcesImplementation(mnSerde.micronaut.serde.jackson)
    testImplementation(mnTest.mockito.core)
    testImplementation(mnTest.mockito.junit.jupiter)
    testRuntimeOnly(mnTest.bytebuddy.agent)
    testRuntimeOnly(mnSql.ojdbc11)
    testRuntimeOnly(mnSql.postgresql)
}

tasks.named("internalStartTestResourcesService") {
    setProperty("useClassDataSharing", false)
}
