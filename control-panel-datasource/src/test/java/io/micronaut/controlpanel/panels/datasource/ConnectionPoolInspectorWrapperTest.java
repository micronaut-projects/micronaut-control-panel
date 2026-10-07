/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.controlpanel.panels.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link ConnectionPoolInspector} must locate its provider's pool even when the pool is behind a
 * wrapping {@link DataSource} — e.g. micronaut-data's {@code ContextualAwareDataSource} or
 * OpenTelemetry's tracing datasource. Relying on {@code instanceof} against the concrete pool type
 * misses the pool whenever such a wrapper is present, leaving the panel with no pool statistics.
 *
 * <p>The fix is to locate the pool via the JDBC {@link java.sql.Wrapper} API ({@code isWrapperFor} /
 * {@code unwrap}), which sees through any conformant wrapper.
 */
class ConnectionPoolInspectorWrapperTest {

    @Test
    @DisplayName("Hikari inspector finds the pool when it is wrapped by another DataSource")
    void hikariInspectorSeesThroughWrapper() {
        try (HikariDataSource hikari = new HikariDataSource()) {
            hikari.setPoolName("wrapped-pool");
            HikariConnectionPoolInspector inspector = new HikariConnectionPoolInspector();

            assertTrue(
                inspector.inspect(hikari).isPresent(),
                "Inspector must report the pool for a bare HikariDataSource"
            );

            DataSource wrapped = new DelegatingTestDataSource(hikari);
            assertTrue(
                inspector.inspect(wrapped).isPresent(),
                "Inspector must report the pool when the HikariDataSource is behind a wrapper"
            );
        }
    }
}
