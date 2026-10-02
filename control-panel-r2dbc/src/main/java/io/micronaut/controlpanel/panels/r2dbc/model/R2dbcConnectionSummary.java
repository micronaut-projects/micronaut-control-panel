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
package io.micronaut.controlpanel.panels.r2dbc.model;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Sanitized R2DBC connection summary.
 *
 * @param name the connection factory bean name
 * @param driver the configured driver
 * @param protocol the configured protocol
 * @param host the configured host
 * @param port the configured port
 * @param database the configured database
 * @param user the configured username
 * @param ssl the configured SSL flag
 * @param connectTimeout the configured connect timeout
 * @param driverMetadataName the driver metadata name
 * @param options safe option names and values
 * @author Sergio del Amo
 * @since 2.0.0
 */
@ReflectiveAccess
public record R2dbcConnectionSummary(
    String name,
    String driver,
    String protocol,
    String host,
    String port,
    String database,
    String user,
    String ssl,
    String connectTimeout,
    String driverMetadataName,
    List<R2dbcOption> options
) {
}
