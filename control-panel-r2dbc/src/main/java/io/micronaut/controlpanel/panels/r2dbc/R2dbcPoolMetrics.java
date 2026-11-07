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

import io.micronaut.core.annotation.Internal;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.ConnectionFactory;
import org.jspecify.annotations.Nullable;

/**
 * Reads the metrics of an R2DBC pool. Kept apart from {@link R2dbcInspector} so that {@code r2dbc-pool} is only loaded
 * when it is on the classpath.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Internal
final class R2dbcPoolMetrics {

    private R2dbcPoolMetrics() {
    }

    static R2dbcFactoryInfo.@Nullable Pool read(ConnectionFactory factory) {
        if (!(factory instanceof ConnectionPool pool)) {
            return null;
        }
        return pool.getMetrics().map(R2dbcPoolMetrics::pool).orElse(null);
    }

    private static R2dbcFactoryInfo.Pool pool(PoolMetrics metrics) {
        return new R2dbcFactoryInfo.Pool(
            metrics.acquiredSize(),
            metrics.allocatedSize(),
            metrics.idleSize(),
            metrics.pendingAcquireSize(),
            metrics.getMaxAllocatedSize(),
            metrics.getMaxPendingAcquireSize()
        );
    }
}
