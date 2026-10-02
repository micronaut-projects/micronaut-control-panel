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
package io.micronaut.controlpanel.panels.spring;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DisabledBean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.controlpanel.panels.spring.SpringAnnotationCatalog.GROUP_ACTUATOR;
import static io.micronaut.controlpanel.panels.spring.SpringAnnotationCatalog.GROUP_CONDITIONS;
import static io.micronaut.controlpanel.panels.spring.SpringAnnotationCatalog.GROUP_WEB;

/**
 * Builds Spring compatibility diagnostics from Micronaut runtime metadata.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Singleton
public final class SpringCompatibilityDiagnostics {

    private static final String REQUIRES_ANNOTATION = "io.micronaut.context.annotation.Requires";
    private static final String ENDPOINT_ANNOTATION = "io.micronaut.management.endpoint.annotation.Endpoint";
    private static final String READ_ANNOTATION = "io.micronaut.management.endpoint.annotation.Read";
    private static final String WRITE_ANNOTATION = "io.micronaut.management.endpoint.annotation.Write";
    private static final String DELETE_ANNOTATION = "io.micronaut.management.endpoint.annotation.Delete";

    private final BeanContext beanContext;
    private final Optional<Router> router;
    private final SpringCompatibilityConfiguration configuration;
    private final SpringAnnotationCatalog catalog = new SpringAnnotationCatalog();
    private final SpringAnnotationValueSanitizer sanitizer = new SpringAnnotationValueSanitizer();

    /**
     * @param beanContext bean context
     * @param router optional router
     * @param configuration configuration
     */
    public SpringCompatibilityDiagnostics(BeanContext beanContext,
                                          Optional<Router> router,
                                          SpringCompatibilityConfiguration configuration) {
        this.beanContext = beanContext;
        this.router = router;
        this.configuration = configuration;
    }

    SpringCompatibilityBody build() {
        List<SpringCompatibilityBody.BeanRow> beans = beans();
        List<SpringCompatibilityBody.RouteRow> routes = routes();
        List<SpringCompatibilityBody.ConditionRow> conditions = conditions();
        List<SpringCompatibilityBody.EndpointRow> endpoints = endpoints();
        List<SpringCompatibilityBody.WarningRow> warnings = configuration.isIncludeUnsupportedFeatureWarnings() ? warnings(routes) : List.of();
        String completeness = dataCompleteness(routes);
        return new SpringCompatibilityBody(
            configuration.isIncludeClasspathSummary() ? detectedModules() : List.of(),
            beans,
            routes,
            conditions,
            endpoints,
            annotationRows(),
            warnings,
            configuration.isIncludeClasspathSummary(),
            configuration.isShowAnnotationValues(),
            completeness
        );
    }

    private List<SpringCompatibilityBody.BeanRow> beans() {
        List<SpringCompatibilityBody.BeanRow> rows = new ArrayList<>();
        for (BeanDefinition<?> beanDefinition : beanContext.getAllBeanDefinitions()) {
            List<String> springAnnotations = annotationNames(beanDefinition.getAnnotationMetadata(), null);
            if (!springAnnotations.isEmpty()) {
                rows.add(toBeanRow(beanDefinition, "active", springAnnotations, ""));
            }
        }
        for (DisabledBean<?> disabledBean : beanContext.getDisabledBeans()) {
            List<String> springAnnotations = annotationNames(disabledBean.getAnnotationMetadata(), null);
            if (!springAnnotations.isEmpty()) {
                rows.add(toBeanRow(disabledBean, "disabled", springAnnotations, String.join("; ", disabledBean.reasons())));
            }
        }
        return rows.stream()
            .sorted(Comparator.comparing(SpringCompatibilityBody.BeanRow::beanType)
                .thenComparing(SpringCompatibilityBody.BeanRow::state))
            .toList();
    }

    private SpringCompatibilityBody.BeanRow toBeanRow(BeanDefinition<?> beanDefinition,
                                                      String state,
                                                      List<String> springAnnotations,
                                                      String requirementSummary) {
        return new SpringCompatibilityBody.BeanRow(
            beanDefinition.getName(),
            beanDefinition.getBeanType().getName(),
            beanDefinition.getScopeName().orElse("default"),
            Optional.ofNullable(beanDefinition.getDeclaredQualifier()).map(Object::toString).orElse(""),
            state,
            springAnnotations,
            mappedAnnotations(beanDefinition.getAnnotationMetadata(), null),
            annotationValuesForGroup(beanDefinition.getAnnotationMetadata(), null),
            requirementSummary
        );
    }

    private List<SpringCompatibilityBody.RouteRow> routes() {
        return router.stream()
            .flatMap(Router::uriRoutes)
            .filter(route -> !annotationNames(route.getTargetMethod().getAnnotationMetadata(), GROUP_WEB).isEmpty())
            .distinct()
            .sorted(Comparator.comparing((UriRouteInfo<?, ?> route) -> route.getUriMatchTemplate().toPathString())
                .thenComparing(UriRouteInfo::getHttpMethodName))
            .map(route -> new SpringCompatibilityBody.RouteRow(
                route.getHttpMethodName(),
                route.getUriMatchTemplate().toPathString(),
                declaringMethod(route.getTargetMethod().getExecutableMethod()),
                annotationNames(route.getTargetMethod().getAnnotationMetadata(), GROUP_WEB),
                mappedAnnotations(route.getTargetMethod().getAnnotationMetadata(), GROUP_WEB),
                annotationValuesForGroup(route.getTargetMethod().getAnnotationMetadata(), GROUP_WEB)
            ))
            .toList();
    }

    private List<SpringCompatibilityBody.ConditionRow> conditions() {
        List<SpringCompatibilityBody.ConditionRow> rows = new ArrayList<>();
        for (BeanDefinition<?> beanDefinition : beanContext.getAllBeanDefinitions()) {
            addConditionRows(rows, beanDefinition, "active", "");
        }
        for (DisabledBean<?> disabledBean : beanContext.getDisabledBeans()) {
            addConditionRows(rows, disabledBean, "disabled", String.join("; ", disabledBean.reasons()));
        }
        return rows.stream()
            .sorted(Comparator.comparing(SpringCompatibilityBody.ConditionRow::beanType)
                .thenComparing(SpringCompatibilityBody.ConditionRow::springAnnotation))
            .toList();
    }

    private void addConditionRows(List<SpringCompatibilityBody.ConditionRow> rows,
                                  BeanDefinition<?> beanDefinition,
                                  String state,
                                  String requirementSummary) {
        for (SpringAnnotationCatalog.SpringAnnotationInfo annotation : catalog.findAll(beanDefinition.getAnnotationMetadata().getAnnotationNames())) {
            if (GROUP_CONDITIONS.equals(annotation.group())) {
                rows.add(new SpringCompatibilityBody.ConditionRow(
                    beanDefinition.getBeanType().getName(),
                    state,
                    annotation.annotationName(),
                    annotation.mappedAnnotationName(),
                    annotationValuesForAnnotation(beanDefinition.getAnnotationMetadata(), annotation.annotationName()),
                    requirementSummary.isBlank() && beanDefinition.getAnnotationMetadata().hasAnnotation(REQUIRES_ANNOTATION)
                        ? "Mapped to @Requires"
                        : requirementSummary
                ));
            }
        }
    }

    private List<SpringCompatibilityBody.EndpointRow> endpoints() {
        List<SpringCompatibilityBody.EndpointRow> rows = new ArrayList<>();
        for (BeanDefinition<?> beanDefinition : beanContext.getAllBeanDefinitions()) {
            if (!annotationNames(beanDefinition.getAnnotationMetadata(), GROUP_ACTUATOR).isEmpty()) {
                String endpointId = endpointId(beanDefinition.getAnnotationMetadata());
                for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
                    List<String> methodAnnotations = annotationNames(method.getAnnotationMetadata(), GROUP_ACTUATOR);
                    if (!methodAnnotations.isEmpty()) {
                        rows.add(new SpringCompatibilityBody.EndpointRow(
                            endpointId,
                            operation(method.getAnnotationMetadata()),
                            declaringMethod(method),
                            "/" + endpointId,
                            methodAnnotations,
                            mappedAnnotations(method.getAnnotationMetadata(), GROUP_ACTUATOR),
                            annotationValuesForGroup(method.getAnnotationMetadata(), GROUP_ACTUATOR)
                        ));
                    }
                }
            }
        }
        return rows.stream()
            .sorted(Comparator.comparing(SpringCompatibilityBody.EndpointRow::endpointId)
                .thenComparing(SpringCompatibilityBody.EndpointRow::operation))
            .toList();
    }

    private List<SpringCompatibilityBody.WarningRow> warnings(List<SpringCompatibilityBody.RouteRow> routes) {
        Set<SpringCompatibilityBody.WarningRow> rows = new LinkedHashSet<>();
        ClassLoader classLoader = beanContext.getClassLoader();
        if (ClassUtils.isPresent("org.aspectj.lang.annotation.Aspect", classLoader)) {
            rows.add(new SpringCompatibilityBody.WarningRow("warning", "classpath", "AspectJ annotations are present. Micronaut Spring does not support AspectJ weaving semantics."));
        }
        for (UriRouteInfo<?, ?> route : router.stream().flatMap(Router::uriRoutes).toList()) {
            for (var argument : route.getTargetMethod().getArguments()) {
                String typeName = argument.getType().getName();
                if (typeName.startsWith("jakarta.servlet.") || typeName.startsWith("javax.servlet.")) {
                    rows.add(new SpringCompatibilityBody.WarningRow("warning", declaringMethod(route.getTargetMethod().getExecutableMethod()), "Servlet API argument detected: " + typeName));
                }
            }
        }
        if (routes.isEmpty()) {
            rows.add(new SpringCompatibilityBody.WarningRow("info", "routes", "No Spring MVC route metadata was detected."));
        }
        return List.copyOf(rows);
    }

    private List<SpringCompatibilityBody.DetectedModule> detectedModules() {
        return List.of(
            module("spring-annotation", "io.micronaut.spring.annotation.context.ComponentAnnotationMapper"),
            module("spring-web-annotation", "io.micronaut.spring.web.annotation.RestControllerAnnotationMapper"),
            module("spring-boot-annotation", "io.micronaut.spring.boot.annotation.EndpointAnnotationMapper"),
            module("spring-context", "io.micronaut.spring.context.MicronautApplicationContext")
        );
    }

    private SpringCompatibilityBody.DetectedModule module(String name, String markerClass) {
        return new SpringCompatibilityBody.DetectedModule(name, markerClass, ClassUtils.isPresent(markerClass, beanContext.getClassLoader()));
    }

    private List<SpringCompatibilityBody.AnnotationSupportRow> annotationRows() {
        return catalog.all().stream()
            .map(annotation -> new SpringCompatibilityBody.AnnotationSupportRow(
                annotation.annotationName(),
                annotation.group(),
                annotation.supportStatus(),
                annotation.mappedAnnotationName()
            ))
            .toList();
    }

    private List<String> annotationNames(AnnotationMetadata metadata, @Nullable String group) {
        return catalog.findAll(allAnnotationNames(metadata)).stream()
            .filter(annotation -> group == null || group.equals(annotation.group()))
            .map(SpringAnnotationCatalog.SpringAnnotationInfo::annotationName)
            .toList();
    }

    private List<String> mappedAnnotations(AnnotationMetadata metadata, @Nullable String group) {
        Set<String> mapped = new LinkedHashSet<>();
        for (SpringAnnotationCatalog.SpringAnnotationInfo annotation : catalog.findAll(allAnnotationNames(metadata))) {
            if (group == null || group.equals(annotation.group())) {
                mapped.add(annotation.mappedAnnotationName());
            }
        }
        return List.copyOf(mapped);
    }

    private List<SpringCompatibilityBody.AnnotationValueRow> annotationValuesForGroup(AnnotationMetadata metadata, @Nullable String group) {
        if (!configuration.isShowAnnotationValues()) {
            return List.of();
        }
        List<SpringCompatibilityBody.AnnotationValueRow> rows = new ArrayList<>();
        for (SpringAnnotationCatalog.SpringAnnotationInfo annotation : catalog.findAll(allAnnotationNames(metadata))) {
            if (group == null || group.equals(annotation.group())) {
                addAnnotationValues(rows, annotation.annotationName(), metadata);
            }
        }
        return rows.stream()
            .sorted(Comparator.comparing(SpringCompatibilityBody.AnnotationValueRow::annotationName)
                .thenComparing(SpringCompatibilityBody.AnnotationValueRow::memberName))
            .toList();
    }

    private List<SpringCompatibilityBody.AnnotationValueRow> annotationValuesForAnnotation(AnnotationMetadata metadata, String annotationName) {
        if (!configuration.isShowAnnotationValues()) {
            return List.of();
        }
        List<SpringCompatibilityBody.AnnotationValueRow> rows = new ArrayList<>();
        addAnnotationValues(rows, annotationName, metadata);
        return rows.stream()
            .sorted(Comparator.comparing(SpringCompatibilityBody.AnnotationValueRow::annotationName)
                .thenComparing(SpringCompatibilityBody.AnnotationValueRow::memberName))
            .toList();
    }

    private void addAnnotationValues(List<SpringCompatibilityBody.AnnotationValueRow> rows,
                                     String annotationName,
                                     AnnotationMetadata metadata) {
        List<AnnotationValue<java.lang.annotation.Annotation>> values = metadata.getAnnotationValuesByName(annotationName);
        if (values.isEmpty()) {
            Map<CharSequence, Object> directValues = metadata.getValues(annotationName);
            if (!directValues.isEmpty()) {
                addAnnotationValueMembers(rows, annotationName, "", directValues);
            }
            return;
        }
        for (AnnotationValue<?> value : values) {
            addAnnotationValueMembers(rows, annotationName, "", value.getValues());
        }
    }

    private void addAnnotationValueMembers(List<SpringCompatibilityBody.AnnotationValueRow> rows,
                                           String annotationName,
                                           String prefix,
                                           Map<CharSequence, Object> values) {
        for (Map.Entry<CharSequence, Object> entry : values.entrySet()) {
            String memberName = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof AnnotationValue<?> annotationValue) {
                addAnnotationValueMembers(rows, annotationName, memberName + ".", annotationValue.getValues());
            } else if (value instanceof AnnotationValue<?>[] annotationValues) {
                for (int i = 0; i < annotationValues.length; i++) {
                    addAnnotationValueMembers(rows, annotationName, memberName + "[" + i + "].", annotationValues[i].getValues());
                }
            } else {
                rows.add(new SpringCompatibilityBody.AnnotationValueRow(
                    annotationName,
                    memberName,
                    sanitizer.summarize(memberName, displayValue(value), true)
                ));
            }
        }
    }

    private static String displayValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Object[] objects) {
            return Arrays.toString(objects);
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            List<String> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(String.valueOf(Array.get(value, i)));
            }
            return values.toString();
        }
        return String.valueOf(value);
    }

    private Set<String> allAnnotationNames(AnnotationMetadata metadata) {
        Set<String> names = new LinkedHashSet<>(metadata.getAnnotationNames());
        names.addAll(metadata.getStereotypeAnnotationNames());
        names.addAll(metadata.getDeclaredAnnotationNames());
        names.addAll(metadata.getDeclaredStereotypeAnnotationNames());
        return names;
    }

    private static String declaringMethod(ExecutableMethod<?, ?> method) {
        return method.getDeclaringType().getName() + "#" + method.getName();
    }

    private static String endpointId(AnnotationMetadata metadata) {
        return metadata.stringValue("org.springframework.boot.actuate.endpoint.annotation.Endpoint", "id")
            .or(() -> metadata.stringValue(ENDPOINT_ANNOTATION, "id"))
            .orElse("unknown");
    }

    private static String operation(AnnotationMetadata metadata) {
        if (metadata.hasAnnotation("org.springframework.boot.actuate.endpoint.annotation.ReadOperation") || metadata.hasAnnotation(READ_ANNOTATION)) {
            return "read";
        }
        if (metadata.hasAnnotation("org.springframework.boot.actuate.endpoint.annotation.WriteOperation") || metadata.hasAnnotation(WRITE_ANNOTATION)) {
            return "write";
        }
        if (metadata.hasAnnotation("org.springframework.boot.actuate.endpoint.annotation.DeleteOperation") || metadata.hasAnnotation(DELETE_ANNOTATION)) {
            return "delete";
        }
        return "operation";
    }

    private String dataCompleteness(List<SpringCompatibilityBody.RouteRow> routes) {
        if (router.isEmpty()) {
            return "Route metadata is unavailable because no Router bean is present.";
        }
        if (routes.isEmpty()) {
            return "No Spring-origin routes were detected. This can mean no Spring web annotations are present or the web annotation mapper is absent.";
        }
        return "Spring-origin metadata is derived from Micronaut annotation metadata. Annotation values are hidden unless explicitly enabled.";
    }
}
