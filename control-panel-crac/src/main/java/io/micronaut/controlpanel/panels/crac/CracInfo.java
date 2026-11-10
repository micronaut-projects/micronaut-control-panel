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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Coordinated Restore at Checkpoint (CRaC) state of the application. Internal to the CRaC panel views.
 *
 * @param enabled whether Micronaut CRaC is enabled ({@code crac.enabled})
 * @param refreshBeans whether beans are refreshed before a checkpoint ({@code crac.refresh-beans})
 * @param datasourcePauseTimeout how long a checkpoint waits for datasource connections to be released
 * @param jvmSupport whether the JVM supports CRaC
 * @param checkpointConfigured whether the JVM was started with {@code -XX:CRaCCheckpointTo}
 * @param restoreTime when the JVM was restored from a checkpoint, or {@code null} when it was not
 * @param uptimeSinceRestore the time since the JVM was restored, or {@code null} when it was not
 * @param resources the resources that take part in checkpoints, in the order they run before a checkpoint
 * @param redis the Redis resources that are enabled, or {@code null} when Redis CRaC support is not present
 * @param events the most recent checkpoint and restore events, newest first
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record CracInfo(
    boolean enabled,
    boolean refreshBeans,
    @Nullable String datasourcePauseTimeout,
    boolean jvmSupport,
    boolean checkpointConfigured,
    @Nullable String restoreTime,
    @Nullable String uptimeSinceRestore,
    List<Resource> resources,
    @Nullable List<String> redis,
    List<Event> events
) {

    /**
     * A resource that takes part in checkpoints.
     *
     * @param type the resource class
     * @param name the bean name, for resources created for each datasource or Redis connection
     * @param order the order; lower values run first before a checkpoint and last after a restore
     */
    @ReflectiveAccess
    public record Resource(String type, @Nullable String name, int order) {
    }

    /**
     * A checkpoint or restore of one resource.
     *
     * @param phase {@code checkpoint} or {@code restore}
     * @param resource the resource class
     * @param time when it happened
     * @param duration how long the resource took
     */
    @ReflectiveAccess
    public record Event(String phase, String resource, String time, String duration) {
    }
}
