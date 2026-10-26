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

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlPanelWriteClassificationTest {

    private static final String CP = "/control-panel";

    @ParameterizedTest
    @ValueSource(strings = {
        "/datasource-control-panel-controller/default/query",
        "/datasource-control-panel-controller/default/query/",
        "/object-storage-control-panel-controller/default",
        "/object-storage-control-panel-controller/default/",
        "/hibernate-control-panel-controller/default/statistics/enabled/true",
        "/hibernate-control-panel-controller/default/statistics/enabled/true/",
        "/loggers-control-panel-controller/io.micronaut",
        "/loggers-control-panel-controller/io.micronaut/",
        "/application-control-panel-controller/refresh/"
    })
    void postWriteRoutesAreClassifiedWithOrWithoutATrailingSlash(String route) {
        assertTrue(ControlPanelSecurityPaths.isWriteRequest(CP, HttpRequest.POST(CP + route, "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/cache-control-panel-controller/demo",
        "/cache-control-panel-controller/demo/",
        "/hibernate-control-panel-controller/default/statistics/",
        "/object-storage-control-panel-controller/default/hello.txt/"
    })
    void deleteWriteRoutesAreClassifiedWithOrWithoutATrailingSlash(String route) {
        assertTrue(ControlPanelSecurityPaths.isWriteRequest(CP, HttpRequest.DELETE(CP + route)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/datasource-control-panel-controller/default/query/",
        "/object-storage-control-panel-controller/default/",
        "/cache-control-panel-controller/demo/"
    })
    void readsStayReadsWithATrailingSlash(String route) {
        assertFalse(ControlPanelSecurityPaths.isWriteRequest(CP, HttpRequest.GET(CP + route)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/datasource-control-panel-controller/default/schema.js/",
        "/object-storage-control-panel-controller/default/hello.txt/"
    })
    void nonWritePostsStayReadsWithATrailingSlash(String route) {
        assertFalse(ControlPanelSecurityPaths.isWriteRequest(CP, HttpRequest.POST(CP + route, "")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/control-panel/"})
    void rootPathsAreNotWrites(String path) {
        assertFalse(ControlPanelSecurityPaths.isWriteRequest(CP, HttpRequest.POST(path, "")));
    }
}
