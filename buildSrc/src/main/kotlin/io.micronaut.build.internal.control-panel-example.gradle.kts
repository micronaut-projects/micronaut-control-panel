import io.micronaut.gradle.MicronautRuntime
import io.micronaut.gradle.MicronautTestRuntime
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.graalvm.buildtools.gradle.tasks.NativeRunTask

plugins {
    id("groovy")
    id("io.micronaut.application")
    id("io.micronaut.test-resources")
    id("com.gradleup.shadow")
    id("com.adarshr.test-logger")
}

application {
    mainClass.set("example.Application")
}
val libs = versionCatalogs.named("libs")
val uiTestsHeadlessProperty = "controlPanel.ui-tests.headless"
val uiTestsHeadless = providers.gradleProperty(uiTestsHeadlessProperty)
    .orElse(providers.systemProperty(uiTestsHeadlessProperty))

micronaut {
    version = libs.findVersion("micronaut-platform").get().toString()
    runtime = MicronautRuntime.NETTY
    testRuntime = MicronautTestRuntime.JUNIT_5
}

configurations.configureEach {
    resolutionStrategy.preferProjectModules()
}

tasks.register<JavaExec>("playwrightCodegen") {
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = sourceSets.test.get().runtimeClasspath
    args("codegen", "http://localhost:8080/control-panel")
}

tasks.register<JavaExec>("playwrightInstall") {
    mainClass.set("com.microsoft.playwright.CLI")
    classpath = sourceSets.test.get().runtimeClasspath
    args("install", "--with-deps")
}

tasks.withType<Test> {
    dependsOn(tasks.named("playwrightInstall"))
    systemProperty("micronaut.test.resources.server.client.read.timeout", "180")
    systemProperty("controlPanelProjectRoot", rootDir.absolutePath)
    uiTestsHeadless.orNull?.let {
        systemProperty(uiTestsHeadlessProperty, it)
    }
}

tasks.named<NativeRunTask>("nativeTest") {
    uiTestsHeadless.orNull?.let {
        runtimeArgs.add("-D$uiTestsHeadlessProperty=$it")
    }
}

configurations.configureEach {
    resolutionStrategy.preferProjectModules()
}

val localControlPanelProjects = listOf(
    ":micronaut-control-panel-cache",
    ":micronaut-control-panel-core",
    ":micronaut-control-panel-datasource",
    ":micronaut-control-panel-hibernate",
    ":micronaut-control-panel-kafka",
    ":micronaut-control-panel-management",
    ":micronaut-control-panel-object-storage",
    ":micronaut-control-panel-ui",
)

val checkLocalControlPanelDependencies = tasks.register("checkLocalControlPanelDependencies") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the example runtime classpath uses local control panel projects."

    doLast {
        val components = configurations.named("runtimeClasspath").get()
            .incoming
            .resolutionResult
            .allComponents
            .map { it.id }

        val resolvedLocalProjects = components
            .filterIsInstance<ProjectComponentIdentifier>()
            .mapTo(mutableSetOf()) { it.projectPath }

        val missingLocalProjects = localControlPanelProjects
            .filterNot(resolvedLocalProjects::contains)

        val externalControlPanelModules = components
            .filterIsInstance<ModuleComponentIdentifier>()
            .filter {
                it.group == "io.micronaut.controlpanel" &&
                    it.module.startsWith("micronaut-control-panel-") &&
                    it.module != "micronaut-control-panel-bom"
            }
            .map { "${it.group}:${it.module}:${it.version}" }
            .sorted()

        if (missingLocalProjects.isNotEmpty() || externalControlPanelModules.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("The example runtime classpath must use local control panel project dependencies.")
                    if (missingLocalProjects.isNotEmpty()) {
                        append(" Missing projects: ")
                        append(missingLocalProjects.joinToString())
                        append(".")
                    }
                    if (externalControlPanelModules.isNotEmpty()) {
                        append(" External modules: ")
                        append(externalControlPanelModules.joinToString())
                        append(".")
                    }
                }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkLocalControlPanelDependencies)
}

configurations.named("nativeImageTestClasspath") {
    resolutionStrategy {
        // Kafka and datasource - tests are @DisabledInNativeImage
        exclude(group = "io.micronaut.kafka")
        exclude(group = "org.apache.kafka")
        exclude(group = "io.confluent")
        exclude(group = "com.oracle.database.jdbc")
        exclude(group = "io.micronaut.sql")
        exclude(group = "org.postgresql")
        // Cloud object storage providers - only local storage is tested in native image
        exclude(group = "io.micronaut.objectstorage", module = "micronaut-object-storage-aws")
        exclude(group = "software.amazon.awssdk")
        exclude(group = "io.micronaut.objectstorage", module = "micronaut-object-storage-azure")
        exclude(group = "com.azure")
        exclude(group = "io.micronaut.objectstorage", module = "micronaut-object-storage-gcp")
        exclude(group = "com.google.cloud")
        exclude(group = "com.google.api.grpc")
        exclude(group = "io.grpc")
        exclude(group = "io.micronaut.objectstorage", module = "micronaut-object-storage-oracle-cloud")
        exclude(group = "com.oracle.oci.sdk")
        // Heavy cache providers - only caffeine and ehcache are tested in native image
        exclude(group = "io.micronaut.cache", module = "micronaut-cache-hazelcast")
        exclude(group = "com.hazelcast")
        exclude(group = "io.micronaut.cache", module = "micronaut-cache-infinispan")
        exclude(group = "org.infinispan")
    }
}

graalvmNative {
    agent {
        enabled.set(false)
        defaultMode.set("conditional")
        modes {
            conditional {
                val filterFile = "src/test/native-image/filters/playwright-filter.json"

                userCodeFilterPath.set(filterFile)
                callerFilterFiles.from(filterFile)
                accessFilterFiles.from(filterFile)
                builtinCallerFilter.set(true)
                builtinHeuristicFilter.set(true)
                enableExperimentalPredefinedClasses.set(true)
                enableExperimentalUnsafeAllocationTracing.set(true)
                trackReflectionMetadata.set(true)
            }
        }
        tasksToInstrumentPredicate.set { task ->
            !task.name.contains("playwright")
        }
        metadataCopy {
            inputTaskNames.addAll("run", "test")
            outputDirectories.add("${rootDir}/doc-examples/example-java/src/main/resources/META-INF/native-image/com.microsoft.playwright/playwright")
            mergeWithExisting.set(false)
        }
    }
    binaries.all {
        resources.autodetect()
        quickBuild.set(true)
    }
}

tasks.named("internalStartTestResourcesService") {
    setProperty("clientTimeout", 180)
    setProperty("useClassDataSharing", false)
//    setProperty("debugServer", true)
}
