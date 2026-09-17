import io.micronaut.build.python.PythonCompile

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

// TODO(python): the documented control panel lives in src/main/python (the `source="main"` snippet) and the test in
// src/test/python. Compiling them separately yields two GraalPy VFS roots whose generated shim modules shadow each
// other at runtime, and the Python compiler resolves the imports of a source file only within its own source root, so
// both roots are merged into one directory compiled with the tests.
val mergePythonSources = tasks.register<Sync>("mergePythonSources") {
    from(layout.projectDirectory.dir("src/main/python"))
    from(layout.projectDirectory.dir("src/test/python"))
    into(layout.buildDirectory.dir("merged-python-sources"))
}
tasks.named("compilePython") {
    enabled = false
}
tasks.named<PythonCompile>("compileTestPython") {
    dependsOn(mergePythonSources)
    source.setFrom(mergePythonSources.map { it.destinationDir })
}

tasks.withType<Test>().configureEach {
    systemProperty("micronaut.python.pool.enabled", "false")
    // Gradle enables assertions in test JVMs; an internal Truffle host-interop assertion trips on varargs overloads
    enableAssertions = false
}
