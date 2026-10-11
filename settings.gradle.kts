import io.micronaut.build.MicronautBuildSettingsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "8.1.3"
}

rootProject.name = "control-panel-parent"

include("control-panel-bom")
include("control-panel-core")
include("control-panel-management")
include("control-panel-object-storage")
include("control-panel-cache")
include("control-panel-datasource")
include("control-panel-hibernate")
include("control-panel-ui")
include("control-panel-kafka")
include("control-panel-tracing")

include("doc-examples:example-java")
include("test-suite-thymeleaf")
include("doc-examples:example-kotlin")
include("doc-examples:example-groovy")
include("doc-examples:example-python")

// May be a no-op or deprecated depending on Gradle version; kept to preserve behavior from Groovy DSL
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

configure<MicronautBuildSettingsExtension> {
    useStandardizedProjectNames.set(true)
    importMicronautCatalog()
    importMicronautCatalog("micronaut-views")
    importMicronautCatalog("micronaut-reactor")
    importMicronautCatalog("micronaut-serde")
    importMicronautCatalog("micronaut-micrometer")
    importMicronautCatalog("micronaut-object-storage")
    importMicronautCatalog("micronaut-cache")
    importMicronautCatalog("micronaut-sql")
    importMicronautCatalog("micronaut-data")
    importMicronautCatalog("micronaut-testresources")
    importMicronautCatalog("micronaut-openapi")
    importMicronautCatalog("micronaut-kafka")
}

// Micronaut Tracing 8.4.0-SNAPSHOT (the trace inspector) comes from the Central snapshots repository. Only io.micronaut
// snapshots are resolved from it. Projects declare their own repositories, so it is added to each project.
// Remove once the build moves to a Micronaut Tracing 8.4.0 release.
gradle.beforeProject {
    repositories.maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }
        content {
            includeGroupByRegex("io\\.micronaut(\\..*)?")
        }
    }
}
