plugins {
    io.micronaut.build.internal.`control-panel-module`
}

dependencies {
    api(projects.micronautControlPanelCore)
    implementation(mnCache.micronaut.cache.core)

    compileOnly(mnCache.micronaut.cache.caffeine)
    compileOnly(mnCache.micronaut.cache.ehcache)
    compileOnly(mnCache.micronaut.cache.hazelcast)
    compileOnly(mnCache.micronaut.cache.infinispan)
}

micronautBuild {
    binaryCompatibility.enabledAfter("1.10.0")
}
