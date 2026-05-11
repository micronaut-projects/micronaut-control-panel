plugins {
    io.micronaut.build.internal.`control-panel-module`
}

val noTomlTestSourceSet = sourceSets.create("noTomlTest") {
    compileClasspath += sourceSets.test.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.test.get().runtimeClasspath
}

val noTomlTest by tasks.registering(Test::class) {
    description = "Runs TOML control panel tests without micronaut-toml on the runtime classpath."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = noTomlTestSourceSet.output.classesDirs
    classpath = noTomlTestSourceSet.runtimeClasspath.filter { !it.name.startsWith("micronaut-toml-") }
    shouldRunAfter(tasks.named("test"))
}

dependencies {
    api(projects.micronautControlPanelCore)

    compileOnly(mnToml.micronaut.toml)

    testAnnotationProcessor(mn.micronaut.inject.java)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(mnToml.micronaut.toml)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
}

tasks.named("check") {
    dependsOn(noTomlTest)
}

micronautBuild {
    binaryCompatibility.enabledAfter("2.0.0")
}
