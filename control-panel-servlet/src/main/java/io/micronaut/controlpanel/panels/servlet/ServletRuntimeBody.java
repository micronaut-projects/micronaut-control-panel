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
package io.micronaut.controlpanel.panels.servlet;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Body model for the Servlet runtime panel.
 *
 * @param available whether a servlet context could be inspected
 * @param runtimeName detected servlet runtime
 * @param runtimeMode embedded, WAR, or unknown mode
 * @param context servlet context metadata
 * @param servlets servlet registrations
 * @param filters filter registrations
 * @param config runtime configuration summaries
 * @param warnings diagnostic warnings
 */
@ReflectiveAccess
public record ServletRuntimeBody(
    boolean available,
    String runtimeName,
    String runtimeMode,
    ServletContextInfo context,
    List<ServletRegistrationInfo> servlets,
    List<FilterRegistrationInfo> filters,
    List<RuntimeConfigInfo> config,
    List<DiagnosticWarning> warnings
) {
}
