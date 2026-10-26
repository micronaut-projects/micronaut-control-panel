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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;

import java.util.List;

/**
 * Shared route prefixes owned by the control panel itself.
 */
@Internal
public final class ControlPanelSecurityPaths {

    public static final String CONTROL_PANEL = "${" + ControlPanelModuleConfiguration.PROPERTY_PATH + ":" + ControlPanelModuleConfiguration.DEFAULT_PATH + "}";
    /**
     * Filter pattern for the control panel path, including the server context path. Filters using it
     * set {@code appendContextPath = false}: the context path is part of the pattern, joined the way
     * the router joins it, because Micronaut skips prepending it to a pattern that merely starts with
     * the same characters (context {@code /app}, control panel path {@code /application}). The
     * pattern is rooted with {@code /} so that a path configured without a leading slash also
     * matches; the Ant matcher ignores the empty segments this leaves for the other configurations.
     */
    public static final String CONTROL_PANEL_FILTER_PATTERN = "/${micronaut.server.context-path:}/" + CONTROL_PANEL;
    /**
     * Filter pattern for every route nested under {@link #CONTROL_PANEL_FILTER_PATTERN}, which
     * includes the helper controllers listed by {@link #helperPaths()}.
     */
    public static final String CONTROL_PANEL_SUBPATHS_FILTER_PATTERN = CONTROL_PANEL_FILTER_PATTERN + "/**";
    public static final String CACHE_PATH = "/cache-control-panel-controller";
    public static final String DATASOURCE_PATH = "/datasource-control-panel-controller";
    public static final String HIBERNATE_PATH = "/hibernate-control-panel-controller";
    public static final String KAFKA_PATH = "/kafka-control-panel-controller";
    public static final String LOGGERS_PATH = "/loggers-control-panel-controller";
    public static final String OBJECT_STORAGE_PATH = "/object-storage-control-panel-controller";
    public static final String APPLICATION_PATH = "/application-control-panel-controller";
    public static final String CACHE = CONTROL_PANEL + CACHE_PATH;
    public static final String DATASOURCE = CONTROL_PANEL + DATASOURCE_PATH;
    public static final String HIBERNATE = CONTROL_PANEL + HIBERNATE_PATH;
    public static final String KAFKA = CONTROL_PANEL + KAFKA_PATH;
    public static final String LOGGERS = CONTROL_PANEL + LOGGERS_PATH;
    public static final String OBJECT_STORAGE = CONTROL_PANEL + OBJECT_STORAGE_PATH;
    public static final String APPLICATION = CONTROL_PANEL + APPLICATION_PATH;

    /**
     * Name of the custom request header the control panel UI sends with its own fetch/XHR calls.
     * A cross-site form or navigation cannot set a custom header, so requiring it blocks
     * cross-site write requests.
     */
    public static final String REQUESTED_WITH_HEADER = "X-Requested-With";

    /**
     * Expected value of {@link #REQUESTED_WITH_HEADER} for control panel UI requests.
     */
    public static final String REQUESTED_WITH_VALUE = "micronaut-control-panel";

    private static final List<String> HELPER_PATHS = List.of(CACHE_PATH, DATASOURCE_PATH, HIBERNATE_PATH, KAFKA_PATH, LOGGERS_PATH, OBJECT_STORAGE_PATH, APPLICATION_PATH);

    private ControlPanelSecurityPaths() {
    }

    /**
     * @return helper route path suffixes that belong to the control panel HTTP surface
     */
    public static List<String> helperPaths() {
        return HELPER_PATHS;
    }

    /**
     * Determines whether a request targets a control-panel-owned write operation.
     *
     * @param controlPanelPath the resolved control panel path, including any application context path
     * @param request the HTTP request
     * @return true if the request mutates application/runtime state
     */
    public static boolean isWriteRequest(String controlPanelPath, HttpRequest<?> request) {
        String path = StringUtils.trimTrailingSlashExceptRoot(request.getPath());
        HttpMethod method = request.getMethod();
        if (method == HttpMethod.DELETE) {
            return isHelperPath(controlPanelPath, CACHE_PATH, path)
                || isHelperPath(controlPanelPath, HIBERNATE_PATH, path)
                || isHelperPath(controlPanelPath, OBJECT_STORAGE_PATH, path);
        }
        if (method == HttpMethod.POST) {
            return isHelperPath(controlPanelPath, LOGGERS_PATH, path)
                || isHelperPath(controlPanelPath, KAFKA_PATH, path)
                || isHelperPath(controlPanelPath, APPLICATION_PATH, path)
                || isObjectStorageUpload(controlPanelPath, path)
                || isDatasourceQuery(controlPanelPath, path)
                || isHibernateStatisticsToggle(controlPanelPath, path);
        }
        return false;
    }

    private static boolean isHelperPath(String controlPanelPath, String helperPath, String path) {
        String prefix = controlPanelPath + helperPath;
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean isObjectStorageUpload(String controlPanelPath, String path) {
        if (!isHelperPath(controlPanelPath, OBJECT_STORAGE_PATH, path)) {
            return false;
        }
        String remainder = path.substring((controlPanelPath + OBJECT_STORAGE_PATH).length());
        return remainder.indexOf('/', 1) < 0;
    }

    private static boolean isDatasourceQuery(String controlPanelPath, String path) {
        return isHelperPath(controlPanelPath, DATASOURCE_PATH, path) && path.endsWith("/query");
    }

    private static boolean isHibernateStatisticsToggle(String controlPanelPath, String path) {
        return isHelperPath(controlPanelPath, HIBERNATE_PATH, path) && path.contains("/statistics/enabled/");
    }
}
