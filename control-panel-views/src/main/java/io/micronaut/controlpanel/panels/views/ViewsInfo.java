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
package io.micronaut.controlpanel.panels.views;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What Micronaut Views is configured to render. Internal to the Views panel views.
 *
 * @param enabled whether Micronaut Views is enabled
 * @param folder the folder templates are loaded from
 * @param filterEnabled whether the filter that renders views is enabled
 * @param csp the Content Security Policy settings, or {@code null} when the CSP filter is disabled
 * @param renderers the views renderers
 * @param modelProcessors the beans that add to or change the model before it is rendered
 * @param routes the routes that render a view
 * @param templatesChecked whether the templates of the routes were looked up
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record ViewsInfo(
    boolean enabled,
    String folder,
    boolean filterEnabled,
    @Nullable Csp csp,
    List<Renderer> renderers,
    List<String> modelProcessors,
    List<ViewRoute> routes,
    boolean templatesChecked
) {

    /**
     * @return the number of routes whose template was not found
     */
    public long missingCount() {
        return routes.stream().filter(route -> ViewRoute.MISSING.equals(route.template())).count();
    }

    /**
     * A views renderer.
     *
     * @param type the renderer class
     * @param mediaTypes the media types it produces
     */
    @ReflectiveAccess
    public record Renderer(String type, List<String> mediaTypes) {
    }

    /**
     * The Content Security Policy settings.
     *
     * @param reportOnly whether the policy is only reported, not enforced
     * @param nonce whether a nonce is generated for each response
     * @param filterPath the paths the policy is added to
     * @param policyDirectives the policy directives, if configured
     */
    @ReflectiveAccess
    public record Csp(boolean reportOnly, boolean nonce, String filterPath, @Nullable String policyDirectives) {
    }

    /**
     * A route that renders a view.
     *
     * @param method the HTTP method
     * @param uri the URI template
     * @param view the view name, or {@code null} when the route returns a {@code ModelAndView} and picks the view itself
     * @param handler the controller method, as {@code Type#method}
     * @param template where the template was found, {@value #MISSING}, {@value #CHECK_FAILED}, or {@code null} when
     *                 it was not looked up
     */
    @ReflectiveAccess
    public record ViewRoute(String method, String uri, @Nullable String view, String handler, @Nullable String template) {

        static final String MISSING = "missing";
        static final String CHECK_FAILED = "check failed";

        /**
         * @return whether the template was not found
         */
        public boolean missing() {
            return MISSING.equals(template);
        }

        /**
         * @return whether looking up the template failed
         */
        public boolean failed() {
            return CHECK_FAILED.equals(template);
        }
    }
}
