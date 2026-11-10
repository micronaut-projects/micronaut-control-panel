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
package io.micronaut.controlpanel.panels.r2dbc;

import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.Nullable;

/**
 * An R2DBC connection factory. Internal to the R2DBC panel views.
 *
 * @param name the name, as in {@code r2dbc.datasources.<name>}
 * @param product the database product the driver reports, such as {@code H2} or {@code PostgreSQL}
 * @param driver the {@code driver} option
 * @param protocol the {@code protocol} option, such as {@code pool} or a driver-specific protocol
 * @param host the {@code host} option
 * @param port the {@code port} option
 * @param database the {@code database} option
 * @param user the {@code user} option
 * @param ssl the {@code ssl} option
 * @param connectTimeout the {@code connectTimeout} option
 * @param pool the pool metrics, when the factory is an R2DBC pool
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@ReflectiveAccess
public record R2dbcFactoryInfo(
    String name,
    @Nullable String product,
    @Nullable String driver,
    @Nullable String protocol,
    @Nullable String host,
    @Nullable String port,
    @Nullable String database,
    @Nullable String user,
    @Nullable String ssl,
    @Nullable String connectTimeout,
    @Nullable Pool pool
) {

    /**
     * The metrics of an R2DBC pool.
     *
     * @param acquired the connections in use
     * @param allocated the connections the pool holds, in use or idle
     * @param idle the idle connections
     * @param pending the subscribers waiting for a connection
     * @param maxAllocated the maximum number of connections
     * @param maxPending the maximum number of waiting subscribers
     */
    @ReflectiveAccess
    public record Pool(int acquired, int allocated, int idle, int pending, int maxAllocated, int maxPending) {

        /**
         * @return the share of the pool in use, from 0 to 100
         */
        public int usagePercent() {
            return maxAllocated <= 0 ? 0 : Math.min(100, Math.round(acquired * 100f / maxAllocated));
        }

        /**
         * @return whether the number of waiting subscribers is limited, which it is not by default
         */
        public boolean maxPendingLimited() {
            return maxPending > 0 && maxPending < Integer.MAX_VALUE;
        }
    }
}
