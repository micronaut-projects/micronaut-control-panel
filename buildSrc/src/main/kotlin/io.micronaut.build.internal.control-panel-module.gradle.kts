plugins {
    id("io.micronaut.build.internal.module")
    id("org.sonatype.gradle.plugins.scan")
}

val ossIndexUsername = System.getenv("OSS_INDEX_USERNAME") ?: project.properties["ossIndexUsername"] as String?
val ossIndexPassword = System.getenv("OSS_INDEX_PASSWORD") ?: project.properties["ossIndexPassword"] as String?
val sonatypePluginConfigured = ossIndexUsername != null && ossIndexPassword != null

ossIndexAudit {
    excludeCoordinates.add("com.google.guava:guava:18.0")
    if (sonatypePluginConfigured) {
        username = ossIndexUsername
        password = ossIndexPassword
    }
}

repositories {
    mavenCentral()
}

val mn = versionCatalogs.named("mn")

dependencies {
    annotationProcessor(mn.findLibrary("micronaut.graal").get())
}
