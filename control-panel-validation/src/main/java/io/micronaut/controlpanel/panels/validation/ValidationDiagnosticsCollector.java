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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodReference;
import io.micronaut.validation.validator.ExecutableMethodValidator;
import io.micronaut.validation.validator.ReactiveValidator;
import io.micronaut.validation.validator.ValidatorConfiguration;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.web.router.Router;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Collects read-only validation diagnostics from Micronaut compile-time metadata.
 *
 * @since 2.0.0
 */
@Singleton
@ReflectiveAccess
final class ValidationDiagnosticsCollector {

    private static final String CONSTRAINT_STEREOTYPE = "jakarta.validation.Constraint";
    private static final String VALIDATED_ELEMENT = "io.micronaut.validation.annotation.ValidatedElement";
    private static final String STATE_AVAILABLE = "available";
    private static final String STATE_CONFIGURED = "configured";
    private static final String STATE_ERROR = "error";

    private final BeanContext beanContext;
    private final Environment environment;
    private final Router router;
    private final ValidationConfiguration configuration;
    private final ValidationPackageFilter packageFilter;
    private final ValidationAttributeSanitizer attributeSanitizer;

    ValidationDiagnosticsCollector(BeanContext beanContext,
                                   Environment environment,
                                   Router router,
                                   ValidationConfiguration configuration,
                                   ValidationPackageFilter packageFilter,
                                   ValidationAttributeSanitizer attributeSanitizer) {
        this.beanContext = beanContext;
        this.environment = environment;
        this.router = router;
        this.configuration = configuration;
        this.packageFilter = packageFilter;
        this.attributeSanitizer = attributeSanitizer;
    }

    ValidationDiagnostics collect() {
        List<ValidationDiagnostics.StateMessage> messages = new ArrayList<>();
        boolean validationEnabled = environment.getProperty("micronaut.validator.enabled", Boolean.class).orElse(true);
        List<ValidationDiagnostics.SummaryItem> summary = collectSummary(validationEnabled, messages);
        List<ValidationDiagnostics.ComponentRow> components = collectComponents(messages);
        List<ValidationDiagnostics.ValidatedElementRow> routes = collectRoutes(messages);
        List<ValidationDiagnostics.ValidatedElementRow> methods = collectMethods(messages);
        List<ValidationDiagnostics.ValidatedElementRow> configurationProperties = collectConfigurationProperties(messages);
        List<ValidationDiagnostics.ValidatedElementRow> classes = collectClasses(configurationProperties, messages);
        int totalConstraints = countConstraints(routes) + countConstraints(methods) + countConstraints(configurationProperties) + countConstraints(classes);
        if (!validationEnabled) {
            messages.add(new ValidationDiagnostics.StateMessage("disabled", "Validation is disabled", "`micronaut.validator.enabled=false` disables runtime validation. Metadata remains visible for diagnostics."));
        } else if (totalConstraints == 0 && components.isEmpty()) {
            messages.add(new ValidationDiagnostics.StateMessage("empty", "No validation metadata found", "Validation is configured, but no application routes, methods, classes, configuration properties, or custom validators matched the active package filters."));
        }
        String status = validationEnabled ? "Enabled" : "Disabled";
        String description = validationEnabled
            ? "Micronaut Validation classes are present. The panel is showing compile-time metadata and provider beans when available."
            : "Micronaut Validation classes are present, but runtime validation is disabled by configuration.";
        return new ValidationDiagnostics(
            validationEnabled,
            status,
            description,
            List.copyOf(summary),
            List.copyOf(components),
            List.copyOf(routes),
            List.copyOf(methods),
            List.copyOf(configurationProperties),
            List.copyOf(classes),
            List.copyOf(messages),
            totalConstraints
        );
    }

    private List<ValidationDiagnostics.SummaryItem> collectSummary(boolean validationEnabled, List<ValidationDiagnostics.StateMessage> messages) {
        List<ValidationDiagnostics.SummaryItem> rows = new ArrayList<>();
        rows.add(new ValidationDiagnostics.SummaryItem("Runtime validation", validationEnabled ? "Enabled" : "Disabled", "Environment", validationEnabled ? STATE_CONFIGURED : "disabled"));
        rows.add(new ValidationDiagnostics.SummaryItem("Attribute mode", configuration.getShowConstraintAttributes().name().toLowerCase(Locale.ROOT), ValidationConfiguration.PREFIX + ".show-constraint-attributes", STATE_CONFIGURED));
        rows.add(new ValidationDiagnostics.SummaryItem("Include packages", configuration.getIncludePackages().isEmpty() ? "Application packages" : String.join(", ", configuration.getIncludePackages()), ValidationConfiguration.PREFIX + ".include-packages", STATE_CONFIGURED));
        rows.add(new ValidationDiagnostics.SummaryItem("Exclude packages", configuration.getExcludePackages().isEmpty() ? "Framework defaults" : String.join(", ", configuration.getExcludePackages()), ValidationConfiguration.PREFIX + ".exclude-packages", STATE_CONFIGURED));
        beanContext.findBean(ValidatorConfiguration.class).ifPresentOrElse(
            cfg -> {
                rows.add(new ValidationDiagnostics.SummaryItem("Validator configuration", cfg.getClass().getName(), "ValidatorConfiguration bean", STATE_AVAILABLE));
                rows.add(new ValidationDiagnostics.SummaryItem("Prepend property path", String.valueOf(cfg.isPrependPropertyPath()), "ValidatorConfiguration", STATE_AVAILABLE));
            },
            () -> messages.add(new ValidationDiagnostics.StateMessage("partial", "Validator configuration bean unavailable", "Provider configuration could not be read, but compile-time validation metadata can still be shown."))
        );
        addProvider(rows, messages, Validator.class, "Jakarta Validator");
        addProvider(rows, messages, ValidatorFactory.class, "Jakarta ValidatorFactory");
        addProvider(rows, messages, io.micronaut.validation.validator.Validator.class, "Micronaut Validator");
        addProvider(rows, messages, ExecutableMethodValidator.class, "ExecutableMethodValidator");
        addProvider(rows, messages, ReactiveValidator.class, "ReactiveValidator");
        return rows;
    }

    private <T> void addProvider(List<ValidationDiagnostics.SummaryItem> rows,
                                 List<ValidationDiagnostics.StateMessage> messages,
                                 Class<T> beanType,
                                 String label) {
        Optional<T> bean = beanContext.findBean(beanType);
        if (bean.isPresent()) {
            rows.add(new ValidationDiagnostics.SummaryItem(label, bean.get().getClass().getName(), "BeanContext", STATE_AVAILABLE));
        } else {
            rows.add(new ValidationDiagnostics.SummaryItem(label, "Not configured", "BeanContext", "unavailable"));
            messages.add(new ValidationDiagnostics.StateMessage("partial", label + " unavailable", "The bean is not configured or is hidden by the current provider. Provider-specific metadata is unavailable."));
        }
    }

    private List<ValidationDiagnostics.ComponentRow> collectComponents(List<ValidationDiagnostics.StateMessage> messages) {
        List<ValidationDiagnostics.ComponentRow> rows = new ArrayList<>();
        addValidatorComponents(rows, ConstraintValidator.class, "Micronaut ConstraintValidator");
        addValidatorComponents(rows, jakarta.validation.ConstraintValidator.class, "Jakarta ConstraintValidator");
        if (rows.isEmpty()) {
            messages.add(new ValidationDiagnostics.StateMessage("empty", "No custom validators detected", "No ConstraintValidator beans matched application package filters."));
        }
        return rows.stream()
            .sorted(Comparator.comparing(ValidationDiagnostics.ComponentRow::beanType).thenComparing(ValidationDiagnostics.ComponentRow::kind))
            .toList();
    }

    private void addValidatorComponents(List<ValidationDiagnostics.ComponentRow> rows, Class<?> validatorType, String kind) {
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(validatorType)) {
            if (packageFilter.includes(definition.getBeanType(), configuration)) {
                rows.add(new ValidationDiagnostics.ComponentRow(
                    kind,
                    definition.getBeanType().getName(),
                    Optional.ofNullable(definition.getDeclaredQualifier()).map(Object::toString).orElse("default"),
                    definition.getScopeName().orElse("unknown"),
                    origin(definition.getBeanType()),
                    "ConstraintValidator bean"
                ));
            }
        }
    }

    private List<ValidationDiagnostics.ValidatedElementRow> collectRoutes(List<ValidationDiagnostics.StateMessage> messages) {
        try {
            return router.uriRoutes()
                .filter(route -> packageFilter.includes(route.getTargetMethod().getDeclaringType(), configuration))
                .map(route -> {
                    MethodReference<?, ?> method = route.getTargetMethod();
                    String name = route.getHttpMethodName() + " " + route.getUriMatchTemplate().toPathString() + " -> " + method.getDeclaringType().getName() + "." + method.getMethodName();
                    return buildMethodRow("Route", name, method, "Router");
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(ValidationDiagnostics.ValidatedElementRow::name))
                .toList();
        } catch (RuntimeException e) {
            messages.add(new ValidationDiagnostics.StateMessage(STATE_ERROR, "Route metadata unavailable", message(e)));
            return List.of();
        }
    }

    private List<ValidationDiagnostics.ValidatedElementRow> collectMethods(List<ValidationDiagnostics.StateMessage> messages) {
        try {
            List<ValidationDiagnostics.ValidatedElementRow> rows = new ArrayList<>();
            for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
                if (!packageFilter.includes(definition.getBeanType(), configuration)) {
                    continue;
                }
                for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                    buildMethodRow("Method", method.getDeclaringType().getName() + "." + method.getMethodName(), method, "ExecutableMethod")
                        .ifPresent(rows::add);
                }
            }
            return rows.stream()
                .sorted(Comparator.comparing(ValidationDiagnostics.ValidatedElementRow::name))
                .toList();
        } catch (RuntimeException e) {
            messages.add(new ValidationDiagnostics.StateMessage(STATE_ERROR, "Bean method metadata unavailable", message(e)));
            return List.of();
        }
    }

    private Optional<ValidationDiagnostics.ValidatedElementRow> buildMethodRow(String kind, String name, MethodReference<?, ?> method, String source) {
        List<ValidationDiagnostics.ConstraintRow> constraints = new ArrayList<>();
        constraints.addAll(extractConstraints(method.getAnnotationMetadata(), "method", source));
        for (Argument<?> argument : method.getArguments()) {
            constraints.addAll(extractConstraints(argument.getAnnotationMetadata(), "parameter " + argument.getName(), source));
            constraints.addAll(extractTypeArgumentConstraints(argument, "parameter " + argument.getName(), source));
        }
        constraints.addAll(extractConstraints(method.getReturnType().getAnnotationMetadata(), "return value", source));
        boolean cascaded = hasCascade(method.getAnnotationMetadata())
            || hasCascade(method.getReturnType().getAnnotationMetadata())
            || List.of(method.getArguments()).stream().anyMatch(arg -> hasCascade(arg.getAnnotationMetadata()));
        boolean validatedElement = method.getAnnotationMetadata().hasStereotype(VALIDATED_ELEMENT);
        if (constraints.isEmpty() && !cascaded && !validatedElement) {
            return Optional.empty();
        }
        return Optional.of(new ValidationDiagnostics.ValidatedElementRow(
            kind,
            name,
            source,
            packageFilter.packageName(method.getDeclaringType()),
            List.copyOf(constraints),
            cascaded,
            validatedElement
        ));
    }

    private List<ValidationDiagnostics.ValidatedElementRow> collectConfigurationProperties(List<ValidationDiagnostics.StateMessage> messages) {
        try {
            List<ValidationDiagnostics.ValidatedElementRow> rows = new ArrayList<>();
            for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
                if (definition.isConfigurationProperties() && packageFilter.includes(definition.getBeanType(), configuration)) {
                    inspectClass(definition.getBeanType(), "Configuration properties", "BeanDefinition/BeanIntrospection").ifPresent(rows::add);
                }
            }
            return rows.stream()
                .sorted(Comparator.comparing(ValidationDiagnostics.ValidatedElementRow::name))
                .toList();
        } catch (RuntimeException e) {
            messages.add(new ValidationDiagnostics.StateMessage(STATE_ERROR, "Configuration property metadata unavailable", message(e)));
            return List.of();
        }
    }

    private List<ValidationDiagnostics.ValidatedElementRow> collectClasses(List<ValidationDiagnostics.ValidatedElementRow> configurationProperties,
                                                                           List<ValidationDiagnostics.StateMessage> messages) {
        try {
            Set<Class<?>> types = new LinkedHashSet<>();
            router.uriRoutes()
                .filter(route -> packageFilter.includes(route.getTargetMethod().getDeclaringType(), configuration))
                .forEach(route -> {
                    for (Argument<?> argument : route.getTargetMethod().getArguments()) {
                        if (packageFilter.includes(argument.getType(), configuration)) {
                            types.add(argument.getType());
                        }
                    }
                });
            for (BeanDefinition<?> definition : beanContext.getAllBeanDefinitions()) {
                if (definition.isConfigurationProperties() && packageFilter.includes(definition.getBeanType(), configuration)) {
                    types.add(definition.getBeanType());
                }
            }
            Set<String> configurationPropertyNames = new LinkedHashSet<>();
            for (ValidationDiagnostics.ValidatedElementRow property : configurationProperties) {
                configurationPropertyNames.add(property.name());
            }
            return types.stream()
                .map(type -> inspectClass(type, "Class", "BeanIntrospection"))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(row -> !configurationPropertyNames.contains(row.name()))
                .sorted(Comparator.comparing(ValidationDiagnostics.ValidatedElementRow::name))
                .toList();
        } catch (RuntimeException e) {
            messages.add(new ValidationDiagnostics.StateMessage(STATE_ERROR, "Class metadata unavailable", message(e)));
            return List.of();
        }
    }

    private Optional<ValidationDiagnostics.ValidatedElementRow> inspectClass(Class<?> type, String kind, String source) {
        try {
            BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(type);
            List<ValidationDiagnostics.ConstraintRow> constraints = new ArrayList<>();
            constraints.addAll(extractConstraints(introspection.getAnnotationMetadata(), "class", source));
            introspection.getBeanProperties().forEach(property -> {
                constraints.addAll(extractConstraints(property.getAnnotationMetadata(), "property " + property.getName(), source));
                constraints.addAll(extractTypeArgumentConstraints(property.asArgument(), "property " + property.getName(), source));
            });
            boolean cascaded = introspection.getBeanProperties().stream().anyMatch(p -> hasCascade(p.getAnnotationMetadata()))
                || hasCascade(introspection.getAnnotationMetadata());
            boolean validatedElement = introspection.getAnnotationMetadata().hasStereotype(VALIDATED_ELEMENT);
            if (constraints.isEmpty() && !cascaded && !validatedElement) {
                return Optional.empty();
            }
            return Optional.of(new ValidationDiagnostics.ValidatedElementRow(
                kind,
                type.getName(),
                source,
                packageFilter.packageName(type),
                List.copyOf(constraints),
                cascaded,
                validatedElement
            ));
        } catch (IntrospectionException _) {
            return Optional.empty();
        }
    }

    private List<ValidationDiagnostics.ConstraintRow> extractConstraints(AnnotationMetadata metadata, String target, String source) {
        List<ValidationDiagnostics.ConstraintRow> rows = new ArrayList<>();
        for (AnnotationValue<Annotation> value : metadata.getAnnotationValuesByStereotype(CONSTRAINT_STEREOTYPE)) {
            rows.add(new ValidationDiagnostics.ConstraintRow(
                simpleName(value.getAnnotationName()),
                target,
                classNames(value.classValues("groups")),
                hasCascade(metadata),
                attributeSanitizer.sanitize(value.getValues(), configuration.getShowConstraintAttributes()),
                source
            ));
        }
        return rows;
    }

    private List<ValidationDiagnostics.ConstraintRow> extractTypeArgumentConstraints(Argument<?> argument, String target, String source) {
        List<ValidationDiagnostics.ConstraintRow> rows = new ArrayList<>();
        for (Argument<?> typeArgument : argument.getTypeParameters()) {
            String typeArgumentTarget = target + " type argument " + typeArgument.getName();
            rows.addAll(extractConstraints(typeArgument.getAnnotationMetadata(), typeArgumentTarget, source));
            rows.addAll(extractTypeArgumentConstraints(typeArgument, typeArgumentTarget, source));
        }
        return rows;
    }

    private static boolean hasCascade(AnnotationMetadata metadata) {
        return metadata.hasAnnotation(Valid.class) || metadata.hasStereotype(Valid.class);
    }

    private static List<String> classNames(Class<?>[] classes) {
        List<String> names = new ArrayList<>(classes.length);
        for (Class<?> type : classes) {
            names.add(type.getName());
        }
        return List.copyOf(names);
    }

    private static String simpleName(String annotationName) {
        int dot = annotationName.lastIndexOf('.');
        return dot > -1 ? annotationName.substring(dot + 1) : annotationName;
    }

    private static String origin(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("io.micronaut.")) {
            return "micronaut";
        }
        if (name.startsWith("java.") || name.startsWith("jakarta.")) {
            return "platform";
        }
        return "application";
    }

    private static int countConstraints(List<ValidationDiagnostics.ValidatedElementRow> rows) {
        return rows.stream().mapToInt(ValidationDiagnostics.ValidatedElementRow::constraintCount).sum();
    }

    private static String message(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
