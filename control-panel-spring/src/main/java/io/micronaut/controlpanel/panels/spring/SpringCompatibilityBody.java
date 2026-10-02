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

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * View model for Spring compatibility diagnostics.
 *
 * @param detectedModules detected Micronaut Spring modules
 * @param beans Spring-origin bean rows
 * @param routes Spring-origin route rows
 * @param conditions Spring Boot condition rows
 * @param endpoints Spring Boot Actuator endpoint rows
 * @param annotations annotation support rows
 * @param warnings conservative warning rows
 * @param classpathSummaryIncluded whether the classpath summary is included
 * @param annotationValuesVisible whether annotation values are visible
 * @param dataCompleteness data completeness message
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@ReflectiveAccess
public record SpringCompatibilityBody(
    List<DetectedModule> detectedModules,
    List<BeanRow> beans,
    List<RouteRow> routes,
    List<ConditionRow> conditions,
    List<EndpointRow> endpoints,
    List<AnnotationSupportRow> annotations,
    List<WarningRow> warnings,
    boolean classpathSummaryIncluded,
    boolean annotationValuesVisible,
    String dataCompleteness) {

    /**
     * @return the number of Spring-origin beans
     */
    public int beanCount() {
        return beans.size();
    }

    /**
     * @return the number of Spring-origin routes
     */
    public int routeCount() {
        return routes.size();
    }

    /**
     * @return the number of Spring Boot condition rows
     */
    public int conditionCount() {
        return conditions.size();
    }

    /**
     * @return the number of actuator endpoint rows
     */
    public int endpointCount() {
        return endpoints.size();
    }

    /**
     * @return the number of warning rows
     */
    public int warningCount() {
        return warnings.size();
    }

    /**
     * @return whether any Spring-origin metadata was discovered
     */
    public boolean hasMetadata() {
        return beanCount() > 0 || routeCount() > 0 || conditionCount() > 0 || endpointCount() > 0;
    }

    /**
     * @param name module display name
     * @param markerClass marker class
     * @param present whether the marker class is present
     */
    @ReflectiveAccess
    public record DetectedModule(String name, String markerClass, boolean present) {
    }

    /**
     * @param beanName bean name
     * @param beanType bean type
     * @param scope scope name
     * @param qualifier qualifier
     * @param state active or disabled
     * @param springAnnotations source Spring annotations
     * @param mappedAnnotations mapped Micronaut annotations
     * @param annotationValues sanitized annotation member value summaries
     * @param requirementSummary disabled requirement summary
     */
    @ReflectiveAccess
    public record BeanRow(
        String beanName,
        String beanType,
        String scope,
        String qualifier,
        String state,
        List<String> springAnnotations,
        List<String> mappedAnnotations,
        List<AnnotationValueRow> annotationValues,
        String requirementSummary) {
    }

    /**
     * @param httpMethod HTTP method
     * @param path route path
     * @param declaringMethod declaring method
     * @param springAnnotations source Spring annotations
     * @param mappedAnnotations mapped Micronaut annotations
     * @param annotationValues sanitized annotation member value summaries
     */
    @ReflectiveAccess
    public record RouteRow(
        String httpMethod,
        String path,
        String declaringMethod,
        List<String> springAnnotations,
        List<String> mappedAnnotations,
        List<AnnotationValueRow> annotationValues) {
    }

    /**
     * @param beanType bean type
     * @param state active or disabled
     * @param springAnnotation Spring Boot condition
     * @param mappedAnnotation mapped Micronaut annotation
     * @param annotationValues sanitized annotation member value summaries
     * @param requirementSummary requirement summary
     */
    @ReflectiveAccess
    public record ConditionRow(
        String beanType,
        String state,
        String springAnnotation,
        String mappedAnnotation,
        List<AnnotationValueRow> annotationValues,
        String requirementSummary) {
    }

    /**
     * @param endpointId endpoint id
     * @param operation operation kind
     * @param declaringMethod declaring method
     * @param managementPath management path
     * @param springAnnotations source Spring annotations
     * @param mappedAnnotations mapped Micronaut annotations
     * @param annotationValues sanitized annotation member value summaries
     */
    @ReflectiveAccess
    public record EndpointRow(
        String endpointId,
        String operation,
        String declaringMethod,
        String managementPath,
        List<String> springAnnotations,
        List<String> mappedAnnotations,
        List<AnnotationValueRow> annotationValues) {
    }

    /**
     * @param annotationName Spring annotation name
     * @param memberName annotation member name
     * @param valueSummary sanitized member value summary
     */
    @ReflectiveAccess
    public record AnnotationValueRow(String annotationName, String memberName, String valueSummary) {
    }

    /**
     * @param annotationName Spring annotation name
     * @param group annotation group
     * @param supportStatus support status
     * @param mappedAnnotationName mapped Micronaut annotation name
     */
    @ReflectiveAccess
    public record AnnotationSupportRow(
        String annotationName,
        String group,
        String supportStatus,
        String mappedAnnotationName) {
    }

    /**
     * @param severity warning severity
     * @param source warning source
     * @param message warning message
     */
    @ReflectiveAccess
    public record WarningRow(String severity, String source, String message) {
    }
}
