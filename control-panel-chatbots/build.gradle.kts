plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)

    implementation(mnSerde.micronaut.serde.jackson)

    compileOnly(mnChatbots.micronaut.chatbots.core)
    compileOnly(mnChatbots.micronaut.chatbots.telegram.core)
    compileOnly(mnChatbots.micronaut.chatbots.telegram.http)
    compileOnly(mnChatbots.micronaut.chatbots.basecamp.core)
    compileOnly(mnChatbots.micronaut.chatbots.basecamp.http)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testImplementation(mn.micronaut.http.server.netty)
    testImplementation(mnChatbots.micronaut.chatbots.core)
    testImplementation(mnChatbots.micronaut.chatbots.telegram.core)
    testImplementation(mnChatbots.micronaut.chatbots.telegram.http)
    testImplementation(mnChatbots.micronaut.chatbots.basecamp.core)
    testImplementation(mnChatbots.micronaut.chatbots.basecamp.http)
    testImplementation(mnChatbots.micronaut.chatbots.telegram.api)
    testImplementation(mnChatbots.micronaut.chatbots.basecamp.api)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
