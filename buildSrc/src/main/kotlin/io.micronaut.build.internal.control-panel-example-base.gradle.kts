// Shared setup of the doc-examples/example-{kotlin,groovy,python} projects backing the snippet:: macros of the user
// guide with `project-base="doc-examples/example"`. They only hold the documented control panel and its test, unlike the
// full example-java application.
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

val mn = versionCatalogs.named("mn")
val mnSerde = versionCatalogs.named("mnSerde")
val mnTest = versionCatalogs.named("mnTest")
val mnLogging = versionCatalogs.named("mnLogging")

dependencies {
    implementation(project(":micronaut-control-panel-management"))
    implementation(mn.findLibrary("micronaut-management").get())
    implementation(mn.findLibrary("micronaut-http-server-netty").get())
    implementation(mnSerde.findLibrary("micronaut-serde-jackson").get())
    runtimeOnly(project(":micronaut-control-panel-ui"))
    runtimeOnly(mnLogging.findLibrary("logback-classic").get())
    runtimeOnly(mn.findLibrary("snakeyaml").get())

    testImplementation(mnTest.findLibrary("micronaut-test-junit5").get())
    testRuntimeOnly(mnTest.findLibrary("junit-jupiter-engine").get())
    testRuntimeOnly(mnTest.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
