plugins {
    // No extra plugins required; this is a convention plugin applied only to the UI module
}

if (project.file("src/main/javascript/rollup.config.mjs").exists()) {
    val jsDir = project.file("src/main/javascript")
    // Use Gradle's conventional generated resources location so they are packaged and served from classpath
    val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/main")

    val npmInstall = tasks.register("npmInstall", Exec::class.java) {
        workingDir = jsDir
        commandLine("npm", "install", "--no-fund", "--no-audit")
        inputs.file(jsDir.resolve("package.json"))
        outputs.dir(jsDir.resolve("node_modules"))
    }

    val rollupBuild = tasks.register("rollupBuild", Exec::class.java) {
        dependsOn(npmInstall)
        workingDir = jsDir
        commandLine("npm", "run", "build", "--silent")
        inputs.files(
            jsDir.resolve("editor.mjs"),
            jsDir.resolve("rollup.config.mjs"),
            jsDir.resolve("package.json")
        )
        doFirst {
            generatedResourcesDir.get().dir("static/js").asFile.mkdirs()
        }
        outputs.file(generatedResourcesDir.map { it.file("static/js/editor.bundle.js") })
    }


    plugins.withId("java") {
        the<org.gradle.api.plugins.JavaPluginExtension>().sourceSets.named("main") {
            resources.srcDir(generatedResourcesDir)
        }
        // Ensure the JS bundle is generated before resources are packaged
        tasks.withType<org.gradle.language.jvm.tasks.ProcessResources>().configureEach {
            dependsOn(rollupBuild)
        }
    }
}
