plugins {
    id("io.micronaut.build.internal.module")
    id("org.sonatype.gradle.plugins.scan")
}

val ossIndexUsername = System.getenv("OSS_INDEX_USERNAME") ?: project.properties["ossIndexUsername"] as String?
val ossIndexPassword = System.getenv("OSS_INDEX_PASSWORD") ?: project.properties["ossIndexPassword"] as String?
val sonatypePluginConfigured = ossIndexUsername != null && ossIndexPassword != null

ossIndexAudit {
    excludeCoordinates.addAll(listOf(
        "com.google.guava:guava:18.0",
        "io.projectreactor.netty:reactor-netty-http:1.3.1" // https://ossindex.sonatype.org/vulnerability/CVE-2025-22227?component-type=maven&component-name=io.projectreactor.netty%2Freactor-netty-http&utm_source=ossindex-client&utm_medium=integration&utm_content=1.8.2
    ))
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
    implementation(mn.findLibrary("jspecify").get())
}

tasks.withType<Test> {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
