import io.micronaut.gradle.MicronautRuntime
import io.micronaut.gradle.MicronautTestRuntime

plugins {
    id("groovy")
    id("io.micronaut.application")
    id("com.gradleup.shadow")
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
}

graalvmNative {
    agent {
        enabled.set(System.getenv("CI") == null)
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
