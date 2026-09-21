plugins {
    id("io.micronaut.build.internal.control-panel-example-base")
    groovy
}

val mn = versionCatalogs.named("mn")
val mnTest = versionCatalogs.named("mnTest")

dependencies {
    // the Groovy compiler picks up the Micronaut AST transformations from the compile classpath
    compileOnly(mn.findLibrary("micronaut-inject-groovy").get())
    testCompileOnly(mn.findLibrary("micronaut-inject-groovy").get())
    testImplementation(mnTest.findLibrary("micronaut-test-spock").get())
}
