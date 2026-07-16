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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A {@link DataSource} that delegates to a target, standing in for any framework wrapper layered
 * over a pool (micronaut-data's {@code ContextualAwareDataSource}, OpenTelemetry's tracing
 * datasource, etc.). It is deliberately <em>not</em> the concrete pool type, so {@code instanceof}
 * checks against the pool fail while the JDBC {@link java.sql.Wrapper} API still sees through it.
 */
final class DelegatingTestDataSource implements DataSource {

    private final DataSource target;

    DelegatingTestDataSource(DataSource target) {
        this.target = target;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return (T) this;
        }
        if (iface.isInstance(target)) {
            return iface.cast(target);
        }
        return target.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || iface.isInstance(target) || target.isWrapperFor(iface);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return target.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return target.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return target.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        target.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        target.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return target.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    }
}
