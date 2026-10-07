plugins {
    id("io.micronaut.build.internal.control-panel-example-base")
    id("io.micronaut.build.internal.python")
}

val libs = versionCatalogs.named("libs")

dependencies {
    implementation(platform(libs.findLibrary("micronaut-core").get()))
    // The Python compiler takes the (jar-resolved) compile classpath as its annotation processor path, so the
    // Micronaut processors are regular dependencies rather than annotationProcessor ones.
    implementation(libs.findLibrary("micronaut-context-python").get())
    testImplementation(libs.findLibrary("micronaut-inject-python-test").get())
}

tasks.withType<Test>().configureEach {
    systemProperty("micronaut.python.pool.enabled", "false")
}
