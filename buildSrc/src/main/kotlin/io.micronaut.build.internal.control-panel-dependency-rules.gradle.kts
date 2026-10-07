dependencies {
    components {
        // micronaut-cache-infinispan 6.1.1 publishes jackson-core as {strictly 2.21.4}, which cannot be resolved
        // together with the jackson-core versions required by micronaut-object-storage and micronaut-kafka.
        // Keep 2.21.4 as the minimum version (2.21.3 is affected by GHSA-r7wm-3cxj-wff9), but drop the strict pin.
        withModule("io.micronaut.cache:micronaut-cache-infinispan") {
            allVariants {
                withDependencyConstraints {
                    filter { it.group == "com.fasterxml.jackson.core" && it.name == "jackson-core" && it.versionConstraint.strictVersion.isNotEmpty() }
                        .forEach { constraint ->
                            val version = constraint.versionConstraint.strictVersion
                            constraint.version { require(version) }
                        }
                }
            }
        }
    }
}
