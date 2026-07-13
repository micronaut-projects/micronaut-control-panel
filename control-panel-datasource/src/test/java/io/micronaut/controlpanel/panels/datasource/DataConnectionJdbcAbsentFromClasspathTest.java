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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the datasource control panel works when {@code micronaut-data-connection-jdbc} is absent
 * from the runtime classpath — the common case for applications that do not use Micronaut Data
 * (raw JDBC, or a standalone SQL layer).
 *
 * <p>The panel is a generic JDBC inspection tool, yet it used to take a runtime ({@code implementation})
 * dependency on {@code micronaut-data-connection-jdbc} solely for {@code DelegatingDataSource}. That
 * forced Micronaut Data's {@code ContextualAwareDataSource} advice onto every consumer and made
 * {@link DataSourceService}'s constructor reference a class that is not present for non-data apps,
 * so instantiating the {@code @EachBean(DataSource.class)} service threw {@code NoClassDefFoundError}.
 *
 * <p>This test is run by the {@code testWithoutDataConnection} Gradle task, which strips
 * {@code micronaut-data-connection-jdbc} from the test classpath.
 */
class DataConnectionJdbcAbsentFromClasspathTest {

    @Test
    void datasourcePanelInstantiatesWithoutDataConnectionJdbc() {
        try (ApplicationContext context = ApplicationContext.run(
            Map.of("spec.name", "DataConnectionJdbcAbsentFromClasspathTest")
        )) {
            // Resolving DataSourceService (@EachBean(DataSource.class)) runs its constructor. While the
            // constructor referenced DelegatingDataSource directly, this threw NoClassDefFoundError once
            // micronaut-data-connection-jdbc was stripped from the classpath.
            assertFalse(
                context.getBeansOfType(DataSourceService.class).isEmpty(),
                "DataSourceService must instantiate when micronaut-data-connection-jdbc is absent"
            );
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "DataConnectionJdbcAbsentFromClasspathTest")
    static class StubDataSourceFactory {

        @Singleton
        DataSource dataSource() {
            return new StubDataSource();
        }
    }

    /**
     * A no-op {@link DataSource}; the test only needs a bean to exist so the {@code @EachBean}
     * {@link DataSourceService} is instantiated. No connection is opened.
     */
    private static final class StubDataSource implements DataSource {

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
