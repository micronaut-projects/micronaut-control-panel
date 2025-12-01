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
package io.micronaut.controlpanel.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import io.micronaut.core.util.StringUtils;

/**
 * Utility class.
 */
@Internal
public final class ControlPanelUtils {

    private ControlPanelUtils() {
    }

    /**
     * Computes the full path for a control panel by normalizing and combining the application path
     * and control panel path. The resulting path is prefixed with a forward slash and formatted as
     * "/appPath/controlPanelPath" if appPath is non-empty, or "/controlPanelPath" if appPath is
     * empty or blank.
     *
     * @param appPath the application path component; must not be null
     * @param controlPanelPath the control panel path component; must not be null
     * @return the computed control panel path as a non-null string
     */
    @NonNull
    public static String computeControlPanelPath(@NonNull String appPath, @NonNull String controlPanelPath) {
        final String normalizedAppPath = normalize(appPath);
        final String normalizedControlPanelPath = normalize(controlPanelPath);
        if (StringUtils.hasText(normalizedAppPath)) {
            return "/%s/%s".formatted(normalizedAppPath, normalizedControlPanelPath);
        } else {
            return "/%s".formatted(normalizedControlPanelPath);
        }
    }

    @NonNull
    private static String normalize(@NonNull String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
}
