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
 * @param validationEnabled whether runtime validation is enabled
 * @param status short validation status label
 * @param statusDescription validation status description
 * @param summary detected capability summary rows
 * @param components validator component rows
 * @param routes validated route rows
 * @param methods validated method rows
 * @param configurationProperties validated configuration property rows
 * @param classes validated class rows
 * @param messages state messages
 * @param totalConstraints total number of collected constraint rows
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

    /**
     * Summary row.
     *
     * @param label capability label
     * @param value capability value
     * @param source metadata source
     * @param state capability state
     */
    @ReflectiveAccess
    public record SummaryItem(String label, String value, String source, String state) {
    }

    /**
     * Validator component row.
     *
     * @param kind validator kind
     * @param beanType validator bean type
     * @param qualifier bean qualifier
     * @param scope bean scope
     * @param origin bean origin
     * @param source metadata source
     */
    @ReflectiveAccess
    public record ComponentRow(String kind, String beanType, String qualifier, String scope, String origin, String source) {
    }

    /**
     * Validated element row.
     *
     * @param kind element kind
     * @param name element name
     * @param source metadata source
     * @param packageName element package name
     * @param constraints constraint rows
     * @param cascaded whether the element is cascaded
     * @param validatedElement whether Micronaut marks the element as validated
     */
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

    /**
     * Constraint row.
     *
     * @param name constraint name
     * @param target constrained target
     * @param groups validation groups
     * @param cascaded whether the target is cascaded
     * @param attributes safe constraint attributes
     * @param source metadata source
     */
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

    /**
     * Constraint annotation attribute row.
     *
     * @param name attribute name
     * @param value display value
     * @param redacted whether the value was redacted
     */
    @ReflectiveAccess
    public record AttributeRow(String name, String value, boolean redacted) {
    }

    /**
     * State message row.
     *
     * @param state state label
     * @param title message title
     * @param message message body
     */
    @ReflectiveAccess
    public record StateMessage(String state, String title, String message) {
    }
}
