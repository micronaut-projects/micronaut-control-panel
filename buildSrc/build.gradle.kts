plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.gradle.micronaut)
    implementation(libs.gradle.kotlin)
    implementation(libs.micronaut.shared.settings)
    // the Kotlin doc example uses the io.micronaut.build.internal.kotlin-kapt convention of micronaut-build
    implementation(providers.gradleProperty("micronaut-build-version").map { "io.micronaut.build.internal:micronaut-kotlin-build-plugins:$it" }.get())
    implementation(libs.shadow.plugin)
    implementation(libs.test.logger.plugin)

    // Provide Node Gradle plugin to precompiled convention plugins (via version catalog)
    implementation(libs.node.gradle.plugin)
}
