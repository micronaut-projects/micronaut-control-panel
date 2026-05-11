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
package io.micronaut.controlpanel.panels.validation;

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * View model for validation diagnostics.
 *
 * @since 2.0.0
 */
@ReflectiveAccess
public record ValidationDiagnostics(
    boolean validationEnabled,
    String status,
    String statusDescription,
    List<SummaryItem> summary,
    List<ComponentRow> components,
    List<ValidatedElementRow> routes,
    List<ValidatedElementRow> methods,
    List<ValidatedElementRow> configurationProperties,
    List<ValidatedElementRow> classes,
    List<StateMessage> messages,
    int totalConstraints
) {

    public boolean hasRoutes() {
        return !routes.isEmpty();
    }

    public boolean hasMethods() {
        return !methods.isEmpty();
    }

    public boolean hasConfigurationProperties() {
        return !configurationProperties.isEmpty();
    }

    public boolean hasClasses() {
        return !classes.isEmpty();
    }

    public boolean hasComponents() {
        return !components.isEmpty();
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    @ReflectiveAccess
    public record SummaryItem(String label, String value, String source, String state) {
    }

    @ReflectiveAccess
    public record ComponentRow(String kind, String beanType, String qualifier, String scope, String origin, String source) {
    }

    @ReflectiveAccess
    public record ValidatedElementRow(String kind,
                                      String name,
                                      String source,
                                      String packageName,
                                      List<ConstraintRow> constraints,
                                      boolean cascaded,
                                      boolean validatedElement) {
        public int constraintCount() {
            return constraints.size();
        }
    }

    @ReflectiveAccess
    public record ConstraintRow(String name,
                                String target,
                                List<String> groups,
                                boolean cascaded,
                                List<AttributeRow> attributes,
                                String source) {
        public boolean hasGroups() {
            return !groups.isEmpty();
        }

        public boolean hasAttributes() {
            return !attributes.isEmpty();
        }
    }

    @ReflectiveAccess
    public record AttributeRow(String name, String value, boolean redacted) {
    }

    @ReflectiveAccess
    public record StateMessage(String state, String title, String message) {
    }
}
