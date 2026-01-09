import com.github.gradle.node.NodeExtension

plugins {
    id("com.github.node-gradle.node")
}

val jsDir = project.layout.projectDirectory.dir("src/main/javascript")
// Use Gradle's conventional generated resources location so they are packaged and served from classpath
val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/main")

// Download a local Node.js + npm distribution so we don't depend on system npm
extensions.configure(NodeExtension::class.java) {
    download.set(true)
    nodeProjectDir.set(jsDir)
}


// Aggregate task to ensure npm install + npm run build have executed
val buildUiBundle = tasks.register("buildUiBundle") {
    dependsOn("npmInstall", "npm_run_build")
    // Ensure destination exists before Gradle considers outputs
    doFirst {
        generatedResourcesDir.get().dir("static/js").asFile.mkdirs()
    }
    inputs.files(
        jsDir.file("editor.mjs"),
        jsDir.file("rollup.config.mjs"),
        jsDir.file("package.json")
    )
    outputs.file(generatedResourcesDir.map { it.file("static/js/editor.bundle.js") })
}

plugins.withId("java") {
    the<JavaPluginExtension>().sourceSets.named("main") {
        resources.srcDir(generatedResourcesDir)
    }
    // Ensure the JS bundle is generated before resources are packaged
    tasks.withType<ProcessResources>().configureEach {
        dependsOn(buildUiBundle)
    }
}
