plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    testImplementation(mn.micronaut.management)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnSpring.micronaut.spring.annotation)
    testImplementation(mnSpring.micronaut.spring.boot.annotation)
    testImplementation(mnSpring.micronaut.spring.web.annotation)
    testImplementation(mnSpring.spring.context)
    testImplementation(mnSpring.spring.boot.starter.web)
    testImplementation(mnViews.micronaut.views.handlebars)
    testImplementation("org.springframework.boot:spring-boot-actuator:${mnSpring.versions.spring.boot.get()}")
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testAnnotationProcessor(mn.micronaut.inject.java)
    testAnnotationProcessor(mnSpring.micronaut.spring.annotation)
    testAnnotationProcessor(mnSpring.micronaut.spring.boot.annotation)
    testAnnotationProcessor(mnSpring.micronaut.spring.web.annotation)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.1")
}
