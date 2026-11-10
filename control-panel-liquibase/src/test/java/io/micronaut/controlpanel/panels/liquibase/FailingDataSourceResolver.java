package io.micronaut.controlpanel.panels.liquibase;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jdbc.DataSourceResolver;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Fails to resolve datasources once {@link #failing} is set, so a test can make a history unreadable after startup.
 */
@Singleton
@Requires(property = "spec.name", value = "FailingDataSourceResolver")
class FailingDataSourceResolver implements DataSourceResolver {

    volatile boolean failing;

    @Override
    public DataSource resolve(DataSource dataSource) {
        if (failing) {
            throw new IllegalStateException("jdbc:h2:mem:secret");
        }
        return dataSource;
    }
}
