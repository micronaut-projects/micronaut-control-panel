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
package io.micronaut.controlpanel.panels.crac;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.crac.CracConfiguration;
import io.micronaut.crac.OrderedResource;
import io.micronaut.crac.resources.redis.CracRedisConfiguration;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Collects read-only CRaC diagnostics for the Control Panel.
 */
@Singleton
@Requires(classes = OrderedResource.class)
final class CracDiagnosticsService {

    private static final List<String> READINESS_NOTES = List.of(
        "Checkpoint files can contain secrets visible to the JVM; store checkpoint artifacts as sensitive files.",
        "If checkpoint fails on open file descriptors, retry with -Djdk.crac.collect-fd-stacktraces=true and inspect CRaC dump logs.",
        "Verify datasource pools can suspend or close connections before checkpoint and reconnect after restore.",
        "Verify Redis clients, caches, and connections are recreated after restore when Redis CRaC support is enabled.",
        "Custom OrderedResource beans should close files, sockets, and threads before checkpoint and recreate them after restore."
    );

    private final BeanContext beanContext;
    private final CracSupportDetector supportDetector;
    private final CracLifecycleEventHistory eventHistory;

    CracDiagnosticsService(BeanContext beanContext,
                           CracSupportDetector supportDetector,
                           CracLifecycleEventHistory eventHistory) {
        this.beanContext = beanContext;
        this.supportDetector = supportDetector;
        this.eventHistory = eventHistory;
    }

    CracDiagnostics diagnostics() {
        return new CracDiagnostics(
            supportDetector.detect(),
            configuration(),
            redisConfiguration(),
            supportDetector.restoreMetrics(),
            resources(),
            eventHistory.recentEvents(),
            READINESS_NOTES
        );
    }

    private CracDiagnostics.Configuration configuration() {
        return beanContext.findBean(CracConfiguration.class)
            .map(configuration -> new CracDiagnostics.Configuration(
                true,
                configuration.isEnabled(),
                configuration.isRefreshBeans(),
                String.valueOf(configuration.getDatasourcePauseTimeout()),
                "Micronaut CRaC configuration bean is available."
            ))
            .orElseGet(() -> new CracDiagnostics.Configuration(
                false,
                false,
                false,
                "Unavailable",
                "Micronaut CRaC configuration bean is not available."
            ));
    }

    private CracDiagnostics.RedisConfiguration redisConfiguration() {
        return beanContext.findBean(CracRedisConfiguration.class)
            .map(configuration -> new CracDiagnostics.RedisConfiguration(
                true,
                configuration.isEnabled(),
                configuration.isClientEnabled(),
                configuration.isCacheEnabled(),
                configuration.isConnectionEnabled(),
                "Redis CRaC configuration bean is available."
            ))
            .orElseGet(() -> new CracDiagnostics.RedisConfiguration(
                false,
                false,
                false,
                false,
                false,
                "Redis CRaC support is not configured or Redis is not on the classpath."
            ));
    }

    private List<CracDiagnostics.Resource> resources() {
        return beanContext.mapOfType(OrderedResource.class)
            .entrySet()
            .stream()
            .map(this::resource)
            .sorted(Comparator.comparingInt(CracDiagnostics.Resource::order)
                .thenComparing(CracDiagnostics.Resource::className))
            .toList();
    }

    private CracDiagnostics.Resource resource(Map.Entry<String, OrderedResource> entry) {
        OrderedResource resource = entry.getValue();
        String category = CracResourceClassifier.category(resource);
        return new CracDiagnostics.Resource(
            resource.getOrder(),
            category,
            entry.getKey(),
            resource.getClass().getName(),
            CracResourceClassifier.simpleName(resource),
            CracResourceClassifier.readinessNote(category)
        );
    }
}
