import io.micronaut.gradle.MicronautRuntime
import io.micronaut.gradle.MicronautTestRuntime

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

micronaut {
    version = libs.findVersion("micronaut-platform").get().toString()
    runtime = MicronautRuntime.NETTY
    testRuntime = MicronautTestRuntime.JUNIT_5
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
}

configurations.named("nativeImageTestClasspath") {
    resolutionStrategy {
        exclude(group = "io.micronaut.kafka")
        exclude(group = "org.apache.kafka")
        exclude(group = "io.confluent")
        exclude(group = "com.oracle.database.jdbc")
        exclude(group = "io.micronaut.sql")
        exclude(group = "org.postgresql")
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
