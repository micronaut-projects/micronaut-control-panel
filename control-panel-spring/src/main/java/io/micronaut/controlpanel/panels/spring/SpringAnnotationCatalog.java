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

import io.micronaut.core.annotation.Internal;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catalog of Spring annotations that Micronaut Spring can map to Micronaut metadata.
 *
 * @author Denis Stepanov
 * @since 2.0.0
 */
@Internal
public final class SpringAnnotationCatalog {

    static final String GROUP_STEREOTYPE = "Stereotype/configuration";
    static final String GROUP_WEB = "Web";
    static final String GROUP_CONDITIONS = "Boot conditions";
    static final String GROUP_ACTUATOR = "Actuator endpoints";
    static final String GROUP_CACHE = "Cache";
    static final String GROUP_TRANSACTION = "Transaction";
    static final String GROUP_EVENTS = "Events";
    static final String GROUP_SCHEDULING = "Scheduling/async";
    static final String GROUP_UNSUPPORTED = "Unsupported signals";

    private static final String SUPPORTED = "supported";
    private static final String WARNING = "warning";

    private final Map<String, SpringAnnotationInfo> annotations;

    public SpringAnnotationCatalog() {
        Map<String, SpringAnnotationInfo> entries = new LinkedHashMap<>();
        add(entries, "org.springframework.stereotype.Component", GROUP_STEREOTYPE, SUPPORTED, "jakarta.inject.Singleton");
        add(entries, "org.springframework.stereotype.Service", GROUP_STEREOTYPE, SUPPORTED, "jakarta.inject.Singleton");
        add(entries, "org.springframework.stereotype.Repository", GROUP_STEREOTYPE, SUPPORTED, "jakarta.inject.Singleton");
        add(entries, "org.springframework.context.annotation.Configuration", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Factory");
        add(entries, "org.springframework.context.annotation.Bean", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Bean");
        add(entries, "org.springframework.beans.factory.annotation.Autowired", GROUP_STEREOTYPE, SUPPORTED, "jakarta.inject.Inject");
        add(entries, "org.springframework.beans.factory.annotation.Value", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Value");
        add(entries, "org.springframework.beans.factory.annotation.Qualifier", GROUP_STEREOTYPE, SUPPORTED, "jakarta.inject.Qualifier");
        add(entries, "org.springframework.context.annotation.Primary", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Primary");
        add(entries, "org.springframework.context.annotation.Fallback", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Secondary");
        add(entries, "org.springframework.context.annotation.Profile", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.context.annotation.Import", GROUP_STEREOTYPE, SUPPORTED, "io.micronaut.context.annotation.Import");

        add(entries, "org.springframework.web.bind.annotation.RestController", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Controller");
        add(entries, "org.springframework.web.bind.annotation.RequestMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.HttpMethodMapping");
        add(entries, "org.springframework.web.bind.annotation.GetMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Get");
        add(entries, "org.springframework.web.bind.annotation.PostMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Post");
        add(entries, "org.springframework.web.bind.annotation.PutMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Put");
        add(entries, "org.springframework.web.bind.annotation.PatchMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Patch");
        add(entries, "org.springframework.web.bind.annotation.DeleteMapping", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Delete");
        add(entries, "org.springframework.web.bind.annotation.RequestParam", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.QueryValue");
        add(entries, "org.springframework.web.bind.annotation.PathVariable", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.PathVariable");
        add(entries, "org.springframework.web.bind.annotation.RequestBody", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Body");
        add(entries, "org.springframework.web.bind.annotation.RequestHeader", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Header");
        add(entries, "org.springframework.web.bind.annotation.ExceptionHandler", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Error");
        add(entries, "org.springframework.web.bind.annotation.ResponseStatus", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.Status");
        add(entries, "org.springframework.web.service.annotation.HttpExchange", GROUP_WEB, SUPPORTED, "io.micronaut.http.annotation.HttpMethodMapping");

        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnBean", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnClass", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnProperties", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requirements");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");
        add(entries, "org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate", GROUP_CONDITIONS, SUPPORTED, "io.micronaut.context.annotation.Requires");

        add(entries, "org.springframework.boot.actuate.endpoint.annotation.Endpoint", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Endpoint");
        add(entries, "org.springframework.boot.actuate.endpoint.annotation.WebEndpoint", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Endpoint");
        add(entries, "org.springframework.boot.actuate.endpoint.annotation.ReadOperation", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Read");
        add(entries, "org.springframework.boot.actuate.endpoint.annotation.WriteOperation", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Write");
        add(entries, "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Delete");
        add(entries, "org.springframework.boot.actuate.endpoint.annotation.Selector", GROUP_ACTUATOR, SUPPORTED, "io.micronaut.management.endpoint.annotation.Selector");

        add(entries, "org.springframework.cache.annotation.Cacheable", GROUP_CACHE, SUPPORTED, "io.micronaut.cache.annotation.Cacheable");
        add(entries, "org.springframework.cache.annotation.CachePut", GROUP_CACHE, SUPPORTED, "io.micronaut.cache.annotation.CachePut");
        add(entries, "org.springframework.cache.annotation.CacheEvict", GROUP_CACHE, SUPPORTED, "io.micronaut.cache.annotation.CacheInvalidate");
        add(entries, "org.springframework.transaction.annotation.Transactional", GROUP_TRANSACTION, SUPPORTED, "io.micronaut.transaction.annotation.Transactional");
        add(entries, "org.springframework.context.event.EventListener", GROUP_EVENTS, SUPPORTED, "io.micronaut.runtime.event.annotation.EventListener");
        add(entries, "org.springframework.scheduling.annotation.Scheduled", GROUP_SCHEDULING, SUPPORTED, "io.micronaut.scheduling.annotation.Scheduled");
        add(entries, "org.springframework.scheduling.annotation.Async", GROUP_SCHEDULING, SUPPORTED, "io.micronaut.scheduling.annotation.Async");

        add(entries, "org.aspectj.lang.annotation.Aspect", GROUP_UNSUPPORTED, WARNING, "Not supported by Micronaut Spring");
        this.annotations = Map.copyOf(entries);
    }

    Optional<SpringAnnotationInfo> find(String annotationName) {
        return Optional.ofNullable(annotations.get(annotationName));
    }

    List<SpringAnnotationInfo> findAll(Collection<String> annotationNames) {
        return annotationNames.stream()
            .map(annotations::get)
            .filter(annotation -> annotation != null)
            .sorted(Comparator.comparing(SpringAnnotationInfo::group).thenComparing(SpringAnnotationInfo::simpleName))
            .toList();
    }

    List<SpringAnnotationInfo> all() {
        return annotations.values().stream()
            .sorted(Comparator.comparing(SpringAnnotationInfo::group).thenComparing(SpringAnnotationInfo::simpleName))
            .toList();
    }

    private static void add(Map<String, SpringAnnotationInfo> entries,
                            String annotationName,
                            String group,
                            String supportStatus,
                            String mappedAnnotationName) {
        entries.put(annotationName, new SpringAnnotationInfo(annotationName, simpleName(annotationName), group, supportStatus, mappedAnnotationName));
    }

    private static String simpleName(String annotationName) {
        int index = annotationName.lastIndexOf('.');
        return index > -1 ? annotationName.substring(index + 1) : annotationName;
    }

    /**
     * A Spring annotation mapping entry.
     *
     * @param annotationName the Spring annotation name
     * @param simpleName the short annotation name
     * @param group the functional group
     * @param supportStatus the support status
     * @param mappedAnnotationName the mapped Micronaut annotation or warning text
     */
    public record SpringAnnotationInfo(
        String annotationName,
        String simpleName,
        String group,
        String supportStatus,
        String mappedAnnotationName) {
    }
}
