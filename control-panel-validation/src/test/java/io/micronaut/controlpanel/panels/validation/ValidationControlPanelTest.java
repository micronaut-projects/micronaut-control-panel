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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationControlPanelTest {

    @Test
    void collectsValidationMetadata() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.validation.include-packages[0]", "io.micronaut.controlpanel.panels.validation",
            "micronaut.control-panel.validation.show-constraint-attributes", "safe"
        ))) {
            ValidationControlPanel panel = context.getBean(ValidationControlPanel.class);
            ValidationDiagnostics body = panel.getBody();

            assertTrue(body.validationEnabled());
            assertTrue(body.totalConstraints() >= 4);
            assertTrue(body.routes().stream().anyMatch(row -> row.name().contains("/validation-test/orders")));
            assertTrue(body.classes().stream().anyMatch(row -> row.name().endsWith("ValidationRequest")));
            assertFalse(body.classes().stream().anyMatch(row -> row.name().endsWith("ValidationSettings")));
            assertTrue(body.configurationProperties().stream().anyMatch(row -> row.name().endsWith("ValidationSettings")));
            assertTrue(body.components().stream().anyMatch(row -> row.beanType().endsWith("TenantCodeValidator")));
            assertTrue(body.routes().stream()
                .flatMap(row -> row.constraints().stream())
                .anyMatch(row -> row.target().startsWith("parameter") || row.target().equals("method")));
            assertTrue(body.classes().stream()
                .filter(row -> row.name().endsWith("ValidationRequest"))
                .flatMap(row -> row.constraints().stream())
                .anyMatch(row -> row.target().contains("nestedTags")
                    && row.target().indexOf("type argument") != row.target().lastIndexOf("type argument")));
        }
    }

    @Test
    void panelCanBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(ValidationControlPanel.ENABLED_PROPERTY, false))) {
            assertFalse(context.containsBean(ValidationControlPanel.class));
            assertFalse(context.containsBean(ValidationConfiguration.class));
        }
    }

    @Test
    void reportsDisabledRuntimeStateGracefully() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.validator.enabled", false,
            "micronaut.control-panel.validation.include-packages[0]", "io.micronaut.controlpanel.panels.validation"
        ))) {
            ValidationDiagnostics body = context.getBean(ValidationControlPanel.class).getBody();

            assertFalse(body.validationEnabled());
            assertTrue(body.hasMessages());
            assertTrue(body.messages().stream().anyMatch(message -> message.state().equals("disabled")));
        }
    }

    @Test
    void reportsEmptyStateAndEmptyBadgeWhenFiltersHideApplicationMetadata() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.control-panel.validation.include-packages[0]", "example.missing"
        ))) {
            ValidationControlPanel panel = context.getBean(ValidationControlPanel.class);
            ValidationDiagnostics body = panel.getBody();

            assertTrue(panel.getBadge().isEmpty());
            assertTrue(body.messages().stream().anyMatch(message -> message.state().equals("empty")));
            assertFalse(body.hasRoutes());
            assertFalse(body.hasComponents());
        }
    }

    @Test
    void packageFilterHonorsIncludesAndExcludes() {
        ValidationPackageFilter filter = new ValidationPackageFilter();
        ValidationConfiguration configuration = new ValidationConfiguration();
        configuration.setIncludePackages(java.util.List.of("io.micronaut.controlpanel.panels.validation"));
        configuration.setExcludePackages(java.util.List.of("io.micronaut.controlpanel.panels.validation.internal"));

        assertTrue(filter.includes(ValidationControlPanelTest.class, configuration));
        assertFalse(filter.includes(String.class, configuration));
        assertFalse(filter.includes(null, configuration));
        assertTrue(filter.packageName(null).isEmpty());
    }

    @Test
    void configurationSettersPreserveSafeDefaultsForNullValues() {
        ValidationConfiguration configuration = new ValidationConfiguration();

        configuration.setIncludePackages(null);
        configuration.setExcludePackages(null);
        configuration.setShowConstraintAttributes(null);

        assertTrue(configuration.getIncludePackages().isEmpty());
        assertTrue(configuration.getExcludePackages().isEmpty());
        assertTrue(configuration.getShowConstraintAttributes() == ValidationConfiguration.AttributeMode.SAFE);
    }

    @Test
    void sanitizerRedactsSecretLikeAttributes() {
        ValidationAttributeSanitizer sanitizer = new ValidationAttributeSanitizer();
        var rows = sanitizer.sanitize(Map.of("apiKey", "abc123", "min", 2), ValidationConfiguration.AttributeMode.SAFE);

        assertTrue(rows.stream().anyMatch(row -> row.name().equals("apiKey") && row.redacted()));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("min") && row.value().equals("2")));
    }

    @Test
    void sanitizerSupportsHiddenAndVerboseModes() {
        ValidationAttributeSanitizer sanitizer = new ValidationAttributeSanitizer();
        Map<CharSequence, Object> values = Map.of("pattern", "abcdefghijklmnopqrstuvwxyz", "values", new int[] {1, 2, 3});

        assertTrue(sanitizer.sanitize(values, ValidationConfiguration.AttributeMode.NONE).isEmpty());
        assertTrue(sanitizer.sanitize(values, ValidationConfiguration.AttributeMode.ALL).stream()
            .anyMatch(row -> row.name().equals("values") && row.value().equals("[1, 2, 3]")));
    }

    @Test
    void sanitizerTruncatesLargeSafeValues() {
        ValidationAttributeSanitizer sanitizer = new ValidationAttributeSanitizer();
        Map<CharSequence, Object> values = Map.of(
            "pattern", "x".repeat(140),
            "values", new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9},
            "custom", new Object()
        );
        var rows = sanitizer.sanitize(values, ValidationConfiguration.AttributeMode.SAFE);

        assertTrue(rows.stream().anyMatch(row -> row.name().equals("pattern") && row.value().endsWith("[truncated]")));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("values") && row.value().contains("9 total")));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("custom") && row.redacted()));
    }

    @Test
    void sanitizerFormatsClassesEnumsAndIterables() {
        ValidationAttributeSanitizer sanitizer = new ValidationAttributeSanitizer();
        Map<CharSequence, Object> values = Map.of(
            "type", ValidationControlPanelTest.class,
            "annotationClass", new AnnotationClassValue<>(ValidationController.class),
            "mode", TestMode.ONE,
            "flags", List.of(true, TestMode.TWO)
        );
        var rows = sanitizer.sanitize(values, ValidationConfiguration.AttributeMode.SAFE);

        assertTrue(rows.stream().anyMatch(row -> row.name().equals("type")
            && row.value().equals(ValidationControlPanelTest.class.getName())));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("annotationClass")
            && row.value().equals(ValidationController.class.getName())));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("mode") && row.value().equals("ONE")));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("flags") && row.value().equals("[true, TWO]")));
    }

    @Test
    void diagnosticsHelperMethodsReportPresentSections() {
        ValidationDiagnostics.ConstraintRow constraint = new ValidationDiagnostics.ConstraintRow(
            "NotBlank",
            "property name",
            List.of(DefaultGroup.class.getName()),
            true,
            List.of(new ValidationDiagnostics.AttributeRow("min", "1", false)),
            "test"
        );
        ValidationDiagnostics.ValidatedElementRow element = new ValidationDiagnostics.ValidatedElementRow(
            "Class",
            "example.Validated",
            "test",
            "example",
            List.of(constraint),
            true,
            true
        );
        ValidationDiagnostics diagnostics = new ValidationDiagnostics(
            true,
            "Enabled",
            "description",
            List.of(new ValidationDiagnostics.SummaryItem("Runtime validation", "Enabled", "test", "available")),
            List.of(new ValidationDiagnostics.ComponentRow("Validator", "example.Validator", "default", "singleton", "application", "test")),
            List.of(element),
            List.of(element),
            List.of(element),
            List.of(element),
            List.of(new ValidationDiagnostics.StateMessage("partial", "title", "message")),
            1
        );

        assertTrue(diagnostics.hasRoutes());
        assertTrue(diagnostics.hasMethods());
        assertTrue(diagnostics.hasConfigurationProperties());
        assertTrue(diagnostics.hasClasses());
        assertTrue(diagnostics.hasComponents());
        assertTrue(diagnostics.hasMessages());
        assertTrue(element.constraintCount() == 1);
        assertTrue(constraint.hasGroups());
        assertTrue(constraint.hasAttributes());
    }

    @Controller("/validation-test")
    static class ValidationController {

        @Post("/orders/{tenant}")
        ValidationResponse create(@TenantCode String tenant, @Body @Valid ValidationRequest request) {
            return new ValidationResponse(tenant, request.email());
        }
    }

    @Introspected
    record ValidationRequest(
        @NotBlank
        @Size(max = 20)
        String name,

        @Email
        String email,

        @Valid
        Nested nested,

        List<List<@NotBlank String>> nestedTags
    ) {
    }

    @Introspected
    record Nested(@NotBlank String value) {
    }

    record ValidationResponse(String tenant, String email) {
    }

    @ConfigurationProperties("validation.test")
    static class ValidationSettings {
        @NotBlank
        private String endpoint = "https://example.com";

        @Positive
        private int retries = 1;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }
    }

    @Documented
    @Constraint(validatedBy = {})
    @Target({ FIELD, PARAMETER, ANNOTATION_TYPE })
    @Retention(RUNTIME)
    @interface TenantCode {
        String message() default "must be a tenant code";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    @Singleton
    static class TenantCodeValidator implements ConstraintValidator<TenantCode, String> {
        @Override
        public boolean isValid(String value, AnnotationValue<TenantCode> annotationMetadata, ConstraintValidatorContext context) {
            return value != null && value.matches("[A-Z][A-Z0-9_-]+");
        }
    }

    enum TestMode {
        ONE,
        TWO
    }

    interface DefaultGroup {
    }
}
