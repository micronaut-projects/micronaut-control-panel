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
 * Redacted configuration or resource attribute row.
 *
 * @param key row key
 * @param value row value
 * @param source row source
 * @param redacted whether the value was redacted
 */
@ReflectiveAccess
@Internal
public record TracingKeyValue(String key, String value, String source, boolean redacted) {
}
