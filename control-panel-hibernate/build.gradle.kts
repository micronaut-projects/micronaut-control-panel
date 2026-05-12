plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnSql.hibernate.core)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnTest.mockito.junit.jupiter)
    testImplementation(mnSql.hibernate.core)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}
