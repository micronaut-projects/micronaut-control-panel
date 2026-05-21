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

import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.List;

/**
 * Read-only CRaC diagnostics rendered by the Control Panel.
 *
 * @param support       CRaC classpath and JDK support state
 * @param configuration effective Micronaut CRaC configuration
 * @param redis         effective Redis CRaC configuration
 * @param restore       restore metrics from the CRaC MXBean
 * @param resources     ordered CRaC resources
 * @param events        recently observed lifecycle events
 * @param notes         non-mutating readiness notes
 */
@ReflectiveAccess
public record CracDiagnostics(
    Support support,
    Configuration configuration,
    RedisConfiguration redis,
    RestoreMetrics restore,
    List<Resource> resources,
    List<LifecycleEvent> events,
    List<String> notes
) {

    /**
     * Returns whether any ordered CRaC resource was discovered.
     */
    public boolean hasResources() {
        return !resources.isEmpty();
    }

    /**
     * Returns whether any lifecycle event has been observed.
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * CRaC support state.
     *
     * @param cracApiPresent       whether the CRaC API is on the classpath
     * @param mxBeanPresent        whether the CRaC MXBean class is on the classpath
     * @param micronautCracPresent whether Micronaut CRaC is on the classpath
     * @param supported            whether CRaC diagnostics can be collected
     * @param message              human-readable support message
     */
    @ReflectiveAccess
    public record Support(boolean cracApiPresent,
                          boolean mxBeanPresent,
                          boolean micronautCracPresent,
                          boolean supported,
                          String message) {
    }

    /**
     * Effective Micronaut CRaC configuration.
     *
     * @param available              whether the configuration bean is available
     * @param enabled                crac.enabled value
     * @param refreshBeans           crac.refresh-beans value
     * @param datasourcePauseTimeout crac.datasource-pause-timeout value
     * @param message                unavailable-state message
     */
    @ReflectiveAccess
    public record Configuration(boolean available,
                                boolean enabled,
                                boolean refreshBeans,
                                String datasourcePauseTimeout,
                                String message) {
    }

    /**
     * Effective Redis CRaC configuration.
     *
     * @param available         whether the Redis CRaC configuration bean is available
     * @param enabled           crac.redis.enabled value
     * @param clientEnabled     crac.redis.client-enabled value
     * @param cacheEnabled      crac.redis.cache-enabled value
     * @param connectionEnabled crac.redis.connection-enabled value
     * @param message           unavailable-state message
     */
    @ReflectiveAccess
    public record RedisConfiguration(boolean available,
                                     boolean enabled,
                                     boolean clientEnabled,
                                     boolean cacheEnabled,
                                     boolean connectionEnabled,
                                     String message) {
    }

    /**
     * Restore metrics exposed by the CRaC MXBean.
     *
     * @param available          whether metrics were readable
     * @param restored           whether a restore appears to have happened
     * @param restoreTime        restore time text
     * @param uptimeSinceRestore uptime-since-restore text
     * @param message            unavailable-state message
     */
    @ReflectiveAccess
    public record RestoreMetrics(boolean available,
                                 boolean restored,
                                 String restoreTime,
                                 String uptimeSinceRestore,
                                 String message) {
    }

    /**
     * Ordered CRaC resource metadata.
     *
     * @param order         resource order
     * @param category      resource category
     * @param beanName      bean name when resolvable
     * @param className     resource class name
     * @param simpleName    resource simple class name
     * @param readinessNote non-mutating readiness note
     */
    @ReflectiveAccess
    public record Resource(int order,
                           String category,
                           String beanName,
                           String className,
                           String simpleName,
                           String readinessNote) {
    }

    /**
     * Recently observed CRaC lifecycle event.
     *
     * @param phase       beforeCheckpoint or afterRestore
     * @param order       resource order
     * @param category    resource category
     * @param beanName    bean name when resolvable
     * @param className   resource class name
     * @param simpleName  resource simple class name
     * @param completedAt event completion instant
     * @param duration    duration text
     */
    @ReflectiveAccess
    public record LifecycleEvent(String phase,
                                 int order,
                                 String category,
                                 String beanName,
                                 String className,
                                 String simpleName,
                                 String completedAt,
                                 String duration) {
    }
}
