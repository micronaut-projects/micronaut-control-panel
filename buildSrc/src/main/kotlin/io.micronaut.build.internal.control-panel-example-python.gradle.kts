plugins {
    id("io.micronaut.build.internal.control-panel-example-base")
    id("io.micronaut.build.internal.python")
}

val libs = versionCatalogs.named("libs")

// The Python compiler (micronaut-inject-python) ships with Micronaut core 5.2+, which this branch does not use yet: only
// this project resolves that core version (see `micronaut-python` in gradle/libs.versions.toml).
micronautBuild {
    python {
        compilerVersion.set(libs.findVersion("micronaut-python").get().toString())
    }
}

dependencies {
    implementation(platform(libs.findLibrary("micronaut-core-python").get()))
    // The Python compiler takes the (jar-resolved) compile classpath as its annotation processor path, so the
    // Micronaut processors are regular dependencies rather than annotationProcessor ones.
    implementation(libs.findLibrary("micronaut-context-python").get())
    testImplementation(libs.findLibrary("micronaut-inject-python-test").get())
}

tasks.withType<Test>().configureEach {
    systemProperty("micronaut.python.pool.enabled", "false")
}
