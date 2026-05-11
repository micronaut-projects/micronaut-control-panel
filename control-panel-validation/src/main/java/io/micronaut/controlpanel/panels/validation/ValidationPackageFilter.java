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
package io.micronaut.controlpanel.panels.validation;

import jakarta.inject.Singleton;

import java.util.List;

/**
 * Applies package visibility rules for validation diagnostics.
 *
 * @since 2.0.0
 */
@Singleton
final class ValidationPackageFilter {

    private static final List<String> DEFAULT_EXCLUDES = List.of(
        "java.",
        "javax.",
        "jakarta.",
        "io.micronaut.",
        "reactor.",
        "org.reactivestreams."
    );

    boolean includes(Class<?> type, ValidationConfiguration configuration) {
        if (type == null) {
            return false;
        }
        Package pkg = type.getPackage();
        String packageName = pkg == null ? "" : pkg.getName();
        List<String> includePackages = configuration.getIncludePackages();
        if (!includePackages.isEmpty()) {
            return startsWithAny(packageName, includePackages) && !startsWithAny(packageName, configuration.getExcludePackages());
        }
        return !startsWithAny(packageName, DEFAULT_EXCLUDES) && !startsWithAny(packageName, configuration.getExcludePackages());
    }

    String packageName(Class<?> type) {
        if (type == null || type.getPackage() == null) {
            return "";
        }
        return type.getPackage().getName();
    }

    private static boolean startsWithAny(String packageName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
