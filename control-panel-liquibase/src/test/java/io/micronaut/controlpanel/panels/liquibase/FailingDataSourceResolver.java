package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fails to resolve datasources once {@link #failing} is set, so a test can make a history unreadable after startup.
 */
@Singleton
@Requires(property = "spec.name", value = "FailingDataSourceResolver")
class FailingDataSourceResolver implements DataSourceResolver {

    volatile boolean failing;
    volatile boolean probeFailing;
    final List<Connection> connections = new CopyOnWriteArrayList<>();

    @Override
    public DataSource resolve(DataSource dataSource) {
        if (failing) {
            throw new IllegalStateException("jdbc:h2:mem:secret");
        }
        // The resolvers are applied until the datasource no longer changes, so an already wrapped one is kept
        if (probeFailing && !Proxy.isProxyClass(dataSource.getClass())) {
            // The connection is obtained, but reading its metadata fails, as when the database goes away meanwhile
            return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                Object result = invoke(dataSource, method, args);
                if (result instanceof Connection connection) {
                    connections.add(connection);
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
                        if (m.getName().equals("getMetaData")) {
                            throw new SQLException("unreachable");
                        }
                        return invoke(connection, m, a);
                    });
                }
                return result;
            });
        }
        return dataSource;
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
