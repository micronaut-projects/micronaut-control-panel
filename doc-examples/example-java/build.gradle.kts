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

    // JMX
    implementation(projects.micronautControlPanelJmx)
    implementation("io.micronaut.jmx:micronaut-jmx")

    // OpenAPI + Swagger UI (views generated at compile-time)
    annotationProcessor(mnOpenapi.micronaut.openapi)
    testAnnotationProcessor(mnOpenapi.micronaut.openapi)
    implementation(mnOpenapi.micronaut.openapi.annotations)


    runtimeOnly(mnLogging.logback.classic)
    runtimeOnly(mn.snakeyaml)

    testImplementation(mn.micronaut.http.client)
    testImplementation(mnTest.micronaut.test.junit5)
    testImplementation(libs.playwright)
    testResourcesImplementation(mn.micronaut.jackson.databind)
    testRuntimeOnly(mnTest.junit.platform.suite)
}

micronaut {
    testResources {
        version = mnTestResources.versions.micronaut.testresources
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
