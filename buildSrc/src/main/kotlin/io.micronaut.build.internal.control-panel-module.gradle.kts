plugins {
    id("io.micronaut.build.internal.module")
}
repositories {
    mavenCentral()
}

configurations.configureEach {
    resolutionStrategy.preferProjectModules()
}

val mn = versionCatalogs.named("mn")

dependencies {
    annotationProcessor(mn.findLibrary("micronaut.graal").get())
    implementation(mn.findLibrary("jspecify").get())
}

tasks.withType<Test> {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
