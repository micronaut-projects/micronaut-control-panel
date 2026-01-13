plugins {
    java
    id("com.github.node-gradle.node")
}

val jsDir = project.layout.projectDirectory.dir("src/main/javascript")
val generatedResourcesDir = layout.buildDirectory.dir("generated/node-resources")

node {
    download = true
    nodeProjectDir = jsDir
}

sourceSets {
    main {
        resources.srcDir(tasks.named("npm_run_build").map { generatedResourcesDir.get() })
    }
}

tasks.named("spotlessJavaMisc") {
    enabled = false
}
