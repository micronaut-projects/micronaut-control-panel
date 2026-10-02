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

/**
 * Servlet context metadata.
 *
 * @param contextPath servlet context path
 * @param serverInfo servlet container server info
 * @param servletApiVersion declared Servlet API version
 * @param effectiveServletApiVersion effective Servlet API version
 * @param virtualServerName virtual server name
 */
@ReflectiveAccess
public record ServletContextInfo(
    String contextPath,
    String serverInfo,
    String servletApiVersion,
    String effectiveServletApiVersion,
    String virtualServerName
) {
}
