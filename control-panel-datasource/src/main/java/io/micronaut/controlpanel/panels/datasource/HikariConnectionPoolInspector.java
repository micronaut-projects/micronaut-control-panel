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
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.durationMillis;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.durationSeconds;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.option;

@Singleton
@Requires(classes = HikariDataSource.class)
final class HikariConnectionPoolInspector implements ConnectionPoolInspector {

    private static final String PROVIDER = "HikariCP";

    @Override
    public Optional<PoolInfo> inspect(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return Optional.empty();
        }
        var poolMxBean = hikariDataSource.getHikariPoolMXBean();
        int active = poolMxBean == null ? 0 : poolMxBean.getActiveConnections();
        int idle = poolMxBean == null ? 0 : poolMxBean.getIdleConnections();
        int total = poolMxBean == null ? 0 : poolMxBean.getTotalConnections();
        int awaiting = poolMxBean == null ? 0 : poolMxBean.getThreadsAwaitingConnection();
        int max = hikariDataSource.getMaximumPoolSize();
        int min = hikariDataSource.getMinimumIdle();
        return Optional.of(new PoolInfo(
            PROVIDER,
            PoolInfoSupport.display(hikariDataSource.getPoolName()),
            hikariDataSource.getClass().getName(),
            PoolInfo.PoolStats.of(active, idle, total, max, min, awaiting),
            optionGroups(hikariDataSource)
        ));
    }

    private static List<PoolInfo.PoolOptionGroup> optionGroups(HikariDataSource dataSource) {
        return List.of(
            PoolInfoSupport.group(
                "Connection",
                option("JDBC URL", dataSource.getJdbcUrl()),
                option("User", dataSource.getUsername()),
                option("Driver class", dataSource.getDriverClassName()),
                option("Datasource class", dataSource.getDataSourceClassName()),
                option("Datasource JNDI", dataSource.getDataSourceJNDI()),
                option("Datasource implementation", PoolInfoSupport.displayType(dataSource.getDataSource())),
                option("Datasource properties", PoolInfoSupport.displayProperties(dataSource.getDataSourceProperties()))
            ),
            PoolInfoSupport.group(
                "Sizing",
                option("Maximum pool size", dataSource.getMaximumPoolSize()),
                option("Minimum idle", dataSource.getMinimumIdle())
            ),
            PoolInfoSupport.group(
                "Timeouts",
                durationMillis("Connection timeout", dataSource.getConnectionTimeout()),
                durationMillis("Validation timeout", dataSource.getValidationTimeout()),
                durationMillis("Idle timeout", dataSource.getIdleTimeout()),
                durationMillis("Max lifetime", dataSource.getMaxLifetime()),
                durationMillis("Leak detection", dataSource.getLeakDetectionThreshold()),
                durationMillis("Keepalive", dataSource.getKeepaliveTime()),
                durationMillis("Initialization fail", dataSource.getInitializationFailTimeout()),
                durationSeconds("Login timeout", PoolInfoSupport.safeInt(dataSource::getLoginTimeout))
            ),
            PoolInfoSupport.group(
                "Validation",
                option("Connection test query", dataSource.getConnectionTestQuery()),
                option("Connection init SQL", dataSource.getConnectionInitSql()),
                option("Validation timeout", PoolInfoSupport.displayDuration(Duration.ofMillis(dataSource.getValidationTimeout())))
            ),
            PoolInfoSupport.group(
                "Behavior",
                option("Running", dataSource.isRunning()),
                option("Closed", dataSource.isClosed()),
                option("Auto commit", dataSource.isAutoCommit()),
                option("Read only", dataSource.isReadOnly()),
                option("Register MBeans", dataSource.isRegisterMbeans()),
                option("Pool suspension", dataSource.isAllowPoolSuspension()),
                option("Isolate internal queries", dataSource.isIsolateInternalQueries()),
                option("Transaction isolation", dataSource.getTransactionIsolation()),
                option("Schema", dataSource.getSchema()),
                option("Catalog", dataSource.getCatalog())
            ),
            PoolInfoSupport.group(
                "Integrations",
                option("Metrics tracker factory", PoolInfoSupport.displayType(dataSource.getMetricsTrackerFactory())),
                option("Metric registry", PoolInfoSupport.displayType(dataSource.getMetricRegistry())),
                option("Health check registry", PoolInfoSupport.displayType(dataSource.getHealthCheckRegistry())),
                option("Health check properties", PoolInfoSupport.displayProperties(dataSource.getHealthCheckProperties())),
                option("Scheduled executor", PoolInfoSupport.displayType(dataSource.getScheduledExecutor())),
                option("Thread factory", PoolInfoSupport.displayType(dataSource.getThreadFactory())),
                option("Credentials provider", dataSource.getCredentialsProviderClassName()),
                option("Credentials provider implementation", PoolInfoSupport.displayType(dataSource.getCredentialsProvider())),
                option("Exception override", dataSource.getExceptionOverrideClassName()),
                option("Exception override implementation", PoolInfoSupport.displayType(dataSource.getExceptionOverride()))
            )
        );
    }
}
