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
package io.micronaut.controlpanel.panels.reactor;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * View model for Reactor diagnostics.
 *
 * @param routes reactive route diagnostics
 * @param clients Reactor HTTP client bean diagnostics
 * @param readiness read-only readiness checks
 * @param errors section errors that did not prevent rendering
 */
@ReflectiveAccess
public record ReactorDiagnosticsBody(
        List<ReactiveRoute> routes,
        List<ReactorClient> clients,
        List<ReadinessCheck> readiness,
        List<SectionError> errors
) {
    /**
     * Counts reactive routes.
     *
     * @return number of reactive routes
     */
    public int routeCount() {
        return routes.size();
    }

    /**
     * Counts Reactor HTTP client beans.
     *
     * @return number of Reactor HTTP client beans
     */
    public int clientCount() {
        return clients.size();
    }

    /**
     * Reactive route view model.
     *
     * @param httpMethodName HTTP method
     * @param uri route URI
     * @param controllerMethod controller method signature
     * @param returnType route return type
     * @param mode inferred Reactor mode
     * @param modeDescription human-readable mode description
     * @param produces produced media types
     * @param consumes consumed media types
     * @param visibleInRoutesPanel whether the route is visible in the standard Routes panel
     */
    @ReflectiveAccess
    public record ReactiveRoute(
            String httpMethodName,
            String uri,
            String controllerMethod,
            String returnType,
            String mode,
            String modeDescription,
            List<String> produces,
            List<String> consumes,
            boolean visibleInRoutesPanel
    ) {
    }

    /**
     * Reactor HTTP client bean view model.
     *
     * @param beanName bean name
     * @param type Reactor client interface type
     * @param qualifier bean qualifier
     */
    @ReflectiveAccess
    public record ReactorClient(
            String beanName,
            String type,
            String qualifier
    ) {
    }

    /**
     * Readiness check view model.
     *
     * @param name check name
     * @param status check status
     * @param detail check detail
     */
    @ReflectiveAccess
    public record ReadinessCheck(
            String name,
            String status,
            String detail
    ) {
    }

    /**
     * Non-fatal section error view model.
     *
     * @param section section name
     * @param message error message
     */
    @ReflectiveAccess
    public record SectionError(
            String section,
            String message
    ) {
    }
}
