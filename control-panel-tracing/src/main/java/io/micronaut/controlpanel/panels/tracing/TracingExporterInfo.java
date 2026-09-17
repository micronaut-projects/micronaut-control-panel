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
package io.micronaut.controlpanel.panels.tracing;

import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.Internal;

/**
 * Exporter diagnostic row.
 *
 * @param signal OpenTelemetry signal name
 * @param exporter configured exporter value
 * @param endpoint configured endpoint value
 * @param status exporter status
 * @param configured whether the exporter was explicitly configured
 */
@ReflectiveAccess
@Internal
public record TracingExporterInfo(String signal, String exporter, String endpoint, String status, boolean configured) {
}
