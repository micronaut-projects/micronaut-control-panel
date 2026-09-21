plugins {
    id("io.micronaut.build.internal.control-panel-example-base")
    id("io.micronaut.build.internal.kotlin-kapt")
}

val mn = versionCatalogs.named("mn")

dependencies {
    kapt(mn.findLibrary("micronaut-inject-java").get())
    kaptTest(mn.findLibrary("micronaut-inject-java").get())
    implementation(mn.findLibrary("kotlin-stdlib-jdk8").get())
}
