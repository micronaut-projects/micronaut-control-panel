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
    implementation(libs.sonatype.scan)
    implementation(libs.micronaut.shared.settings)
    implementation(libs.shadow.plugin)
    implementation(libs.test.logger.plugin)
}
