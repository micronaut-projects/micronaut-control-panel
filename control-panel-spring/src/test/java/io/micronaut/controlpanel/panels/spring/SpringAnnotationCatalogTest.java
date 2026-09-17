/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.controlpanel.panels.spring;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringAnnotationCatalogTest {

    @Test
    void catalogContainsExpectedGroupsAndMappings() {
        SpringAnnotationCatalog catalog = new SpringAnnotationCatalog();

        Set<String> groups = catalog.all().stream()
            .map(SpringAnnotationCatalog.SpringAnnotationInfo::group)
            .collect(Collectors.toSet());

        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_STEREOTYPE));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_WEB));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_CONDITIONS));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_ACTUATOR));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_CACHE));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_TRANSACTION));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_EVENTS));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_SCHEDULING));
        assertTrue(groups.contains(SpringAnnotationCatalog.GROUP_UNSUPPORTED));
        assertTrue(catalog.all().stream().allMatch(annotation -> !annotation.mappedAnnotationName().isBlank()));
    }

    @Test
    void sanitizerHidesValuesByDefaultAndRedactsSensitiveNames() {
        SpringAnnotationValueSanitizer sanitizer = new SpringAnnotationValueSanitizer();

        assertEquals(SpringAnnotationValueSanitizer.HIDDEN, sanitizer.summarize("name", "value", false));
        assertEquals(SpringAnnotationValueSanitizer.REDACTED, sanitizer.summarize("clientSecret", "value", true));
        assertEquals(SpringAnnotationValueSanitizer.REDACTED, sanitizer.summarize("name", "password=value", true));
        assertNotEquals(SpringAnnotationValueSanitizer.REDACTED, sanitizer.summarize("name", "safe", true));
    }
}
