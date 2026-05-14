import io.micronaut.testresources.buildtools.KnownModules

plugins {
    io.micronaut.build.internal.`control-panel-example`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

dependencies {
    annotationProcessor(mnSerde.micronaut.serde.processor)
    annotationProcessor(mnData.micronaut.data.processor)
    implementation(mnSerde.micronaut.serde.jackson)

    runtimeOnly(projects.micronautControlPanelUi)
    implementation(projects.micronautControlPanelManagement)
    implementation(mn.micronaut.management)
    implementation(mnMicrometer.micronaut.micrometer.core)

    // Object Storage
    implementation(projects.micronautControlPanelObjectStorage)
    implementation(mnObjectStorage.micronaut.`object`.storage.local)
    implementation(mnObjectStorage.micronaut.`object`.storage.aws)
    implementation(mnObjectStorage.micronaut.`object`.storage.azure)
    implementation(mnObjectStorage.micronaut.`object`.storage.gcp)
    implementation(mnObjectStorage.micronaut.`object`.storage.oracle.cloud)

    // Cache
    implementation(projects.micronautControlPanelCache)
    runtimeOnly(mnCache.micronaut.cache.management)
    implementation(mnCache.micronaut.cache.caffeine)
    implementation(mnCache.micronaut.cache.ehcache)
    implementation(mnCache.micronaut.cache.hazelcast)
    implementation(mnCache.micronaut.cache.infinispan)

    //Datasource
    implementation(projects.micronautControlPanelDatasource)
    implementation(mnSql.micronaut.jdbc.hikari)
    implementation(mnData.micronaut.data.jdbc)
    runtimeOnly(mnSql.ojdbc11)
    runtimeOnly(mnSql.postgresql)

    // Hibernate
    implementation(projects.micronautControlPanelHibernate)
    implementation(mnSql.micronaut.hibernate.jpa)
    implementation(mnSql.hibernate.jcache)

    // Kafka
    implementation(projects.micronautControlPanelKafka)
    implementation(mnKafka.micronaut.kafka)
    implementation(mnKafka.micronaut.kafka.streams)
    implementation(libs.avro)
    implementation(libs.avro.serde)

    // Spring Compatibility
    annotationProcessor(mnSpring.micronaut.spring.annotation)
    annotationProcessor(mnSpring.micronaut.spring.boot.annotation)
    annotationProcessor(mnSpring.micronaut.spring.web.annotation)
    implementation(projects.micronautControlPanelSpring)
    implementation(mnSpring.micronaut.spring.annotation)
    implementation(mnSpring.micronaut.spring.boot.annotation)
    implementation(mnSpring.micronaut.spring.web.annotation)
    implementation(mnSpring.spring.context)
    implementation(mnSpring.spring.boot.starter.web)
    implementation("org.springframework.boot:spring-boot-actuator:${mnSpring.versions.spring.boot.get()}")

    // OpenAPI + Swagger UI (views generated at compile-time)
    annotationProcessor(mnOpenapi.micronaut.openapi)
    testAnnotationProcessor(mnOpenapi.micronaut.openapi)
    implementation(mnOpenapi.micronaut.openapi.annotations)


    runtimeOnly(mnLogging.logback.classic)
    runtimeOnly(mn.snakeyaml)

    // The test resources Control Panel service resolves the published RC artifacts.
    testResourcesRuntimeOnly(mn.micronaut.discovery.core)
    testResourcesRuntimeOnly(mn.micronaut.management)

    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.playwright)
    testRuntimeOnly(mnTest.junit.platform.suite)
}

micronaut {
    testResources {
        version = mnTestResources.versions.micronaut.testresources
        additionalModules.add(KnownModules.CONTROL_PANEL)
        additionalModules.add(KnownModules.JDBC_ORACLE_FREE)
        additionalModules.add(KnownModules.JDBC_POSTGRESQL)
        additionalModules.add(KnownModules.KAFKA)
        additionalModules.add("infinispan")
        additionalModules.add("hazelcast")
    }
}

sourceSets {
    main {
        resources {
            srcDirs("src/main/avro")
        }
    }
}

testlogger {
    showStandardStreams = true
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}
