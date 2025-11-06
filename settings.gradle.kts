import io.micronaut.build.MicronautBuildSettingsExtension

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("io.micronaut.build.shared.settings") version "8.0.0-M9"
}

rootProject.name = "control-panel-parent"

include("control-panel-bom")
include("control-panel-core")
include("control-panel-management")
include("control-panel-object-storage")
include("control-panel-ui")

include("doc-examples:example-java")

// May be a no-op or deprecated depending on Gradle version; kept to preserve behavior from Groovy DSL
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

configure<MicronautBuildSettingsExtension> {
    useStandardizedProjectNames.set(true)
    importMicronautCatalog()
    importMicronautCatalog("micronaut-views")
    importMicronautCatalog("micronaut-reactor")
    importMicronautCatalog("micronaut-serde")
    importMicronautCatalog("micronaut-object-storage")
}
