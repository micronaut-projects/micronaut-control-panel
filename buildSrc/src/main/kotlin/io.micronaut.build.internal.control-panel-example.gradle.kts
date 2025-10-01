import io.micronaut.gradle.MicronautRuntime
import io.micronaut.gradle.MicronautTestRuntime

plugins {
    id("groovy")
    id("io.micronaut.application")
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

tasks.named<Test>("test") {
    dependsOn(tasks.named("playwrightInstall"))
}

graalvmNative {
    agent {
        enabled.set(true)
        defaultMode.set("conditional")
        modes {
            conditional {
                userCodeFilterPath.set("src/main/native-image/filters/user-code-filter.json")
                callerFilterFiles.from("src/main/native-image/filters/user-code-filter.json")
                accessFilterFiles.from("src/main/native-image/filters/user-code-filter.json")
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
            outputDirectories.add("${rootDir.toString()}/control-panel-ui/src/main/resources/META-INF/native-image/io.micronaut.controlpanel/micronaut-control-panel-ui")
            mergeWithExisting.set(false)
        }
    }
    binaries.all {
        resources.autodetect()
        quickBuild.set(true)
    }
}
