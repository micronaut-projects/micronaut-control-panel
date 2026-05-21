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
package io.micronaut.controlpanel.panels.email;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

@ReflectiveAccess
record EmailDiagnosticModel(
    String senderName,
    String provider,
    List<String> supportedInterfaces,
    List<String> implementationTypes,
    String defaultFromStatus,
    String defaultFrom,
    String templateSupportStatus,
    List<ConfigurationEntry> configuration,
    List<ChecklistItem> checklist,
    TestSendState testSend,
    boolean empty
) {
    boolean hasConfiguration() {
        return !configuration.isEmpty();
    }

    boolean hasChecklist() {
        return !checklist.isEmpty();
    }

    @ReflectiveAccess
    record ConfigurationEntry(String label, String value, String status) { }

    @ReflectiveAccess
    record ChecklistItem(String label, String status, String message) { }

    @ReflectiveAccess
    record TestSendState(boolean enabled, boolean configured, boolean allowArbitraryRecipient, String status, String message) { }
}
