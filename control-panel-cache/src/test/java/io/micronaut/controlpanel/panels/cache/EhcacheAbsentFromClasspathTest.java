/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.controlpanel.panels.cache;

import io.micronaut.context.annotation.Property;
import io.micronaut.controlpanel.core.ControlPanelRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the control panel repository is queryable when Ehcache is absent from the
 * runtime classpath.
 *
 * <p>Without {@code @Requires(classes = EhcacheSyncCache.class)} on {@link EhcacheControlPanel},
 * Micronaut's service loader fails with {@code NoClassDefFoundError} for {@code EhcacheSyncCache}
 * while loading the generated bean definition. This surfaces as a {@code BeanInstantiationException}
 * when {@link ControlPanelRepository} first calls {@code getBeansOfType(ControlPanel.class)}.
 *
 * <p>This test is run by the {@code testWithoutEhcache} Gradle task, which strips Ehcache JARs
 * from the test classpath.
 */
@MicronautTest
@Property(name = "micronaut.caches.mycache.initialCapacity", value = "1")
class EhcacheAbsentFromClasspathTest {

    @Test
    void controlPanelRepositoryIsQueryableWithoutEhcache(ControlPanelRepository repository) {
        // Triggers getBeansOfType(ControlPanel.class), the code path that loads
        // $EhcacheControlPanel$Definition. Without @Requires(classes = EhcacheSyncCache.class),
        // this throws BeanInstantiationException wrapping NoClassDefFoundError.
        assertTrue(
            repository.findByName("cache-mycache").isPresent(),
            "Caffeine cache panel must be registered even when Ehcache is absent from the classpath"
        );
    }
}
