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
package io.micronaut.controlpanel.core.security;

import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Shared route prefixes owned by the control panel itself.
 */
@Internal
public final class ControlPanelSecurityPaths {

    public static final String CONTROL_PANEL = "${" + ControlPanelModuleConfiguration.PROPERTY_PATH + ":" + ControlPanelModuleConfiguration.DEFAULT_PATH + "}";
    public static final String CACHE_PATH = "/cache-control-panel-controller";
    public static final String DATASOURCE_PATH = "/datasource-control-panel-controller";
    public static final String HIBERNATE_PATH = "/hibernate-control-panel-controller";
    public static final String OBJECT_STORAGE_PATH = "/object-storage-control-panel-controller";
    public static final String CACHE = CONTROL_PANEL + CACHE_PATH;
    public static final String DATASOURCE = CONTROL_PANEL + DATASOURCE_PATH;
    public static final String HIBERNATE = CONTROL_PANEL + HIBERNATE_PATH;
    public static final String OBJECT_STORAGE = CONTROL_PANEL + OBJECT_STORAGE_PATH;

    private static final List<String> HELPER_PATHS = List.of(CACHE_PATH, DATASOURCE_PATH, HIBERNATE_PATH, OBJECT_STORAGE_PATH);

    private ControlPanelSecurityPaths() {
    }

    /**
     * @return helper route path suffixes that belong to the control panel HTTP surface
     */
    public static List<String> helperPaths() {
        return HELPER_PATHS;
    }
}
