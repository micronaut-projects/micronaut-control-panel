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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.panels.datasource.model.PoolInfo;
import jakarta.inject.Singleton;
import oracle.ucp.ShardConnectionStatistics;
import oracle.ucp.UniversalConnectionPoolStatistics;
import oracle.ucp.jdbc.PoolDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.durationMillis;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.durationSeconds;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.option;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.safeBoolean;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.safeDuration;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.safeInt;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.safeLong;
import static io.micronaut.controlpanel.panels.datasource.PoolInfoSupport.safeString;

@Singleton
@Requires(classes = PoolDataSource.class)
final class OracleUcpConnectionPoolInspector implements ConnectionPoolInspector {

    private static final String PROVIDER = "Oracle UCP";

    @Override
    public Optional<PoolInfo> inspect(DataSource dataSource) {
        if (!(dataSource instanceof PoolDataSource poolDataSource)) {
            return Optional.empty();
        }
        var stats = poolDataSource.getStatistics();
        int active = stats == null ? safeInt(poolDataSource::getBorrowedConnectionsCount) : stats.getBorrowedConnectionsCount();
        int idle = stats == null ? safeInt(poolDataSource::getAvailableConnectionsCount) : stats.getAvailableConnectionsCount();
        int total = stats == null ? active + idle : stats.getTotalConnectionsCount();
        int awaiting = stats == null ? 0 : stats.getPendingRequestsCount();
        int max = safeInt(poolDataSource::getMaxPoolSize);
        int min = safeInt(poolDataSource::getMinPoolSize);
        return Optional.of(new PoolInfo(
            PROVIDER,
            safeString(poolDataSource::getConnectionPoolName),
            poolDataSource.getClass().getName(),
            PoolInfo.PoolStats.of(active, idle, total, max, min, awaiting),
            optionGroups(poolDataSource, stats)
        ));
    }

    private static List<PoolInfo.PoolOptionGroup> optionGroups(PoolDataSource dataSource, UniversalConnectionPoolStatistics stats) {
        return List.of(
            PoolInfoSupport.group(
                "Connection",
                option("JDBC URL", safeString(dataSource::getURL)),
                option("User", safeString(dataSource::getUser)),
                option("Connection factory", safeString(dataSource::getConnectionFactoryClassName)),
                option("Datasource name", safeString(dataSource::getDataSourceName)),
                option("Description", safeString(dataSource::getDescription)),
                option("Service name", PoolInfoSupport.display(dataSource.getServiceName())),
                option("Server name", safeString(dataSource::getServerName)),
                option("Port number", safeInt(dataSource::getPortNumber)),
                option("Database name", safeString(dataSource::getDatabaseName)),
                option("Network protocol", safeString(dataSource::getNetworkProtocol)),
                option("Role name", safeString(dataSource::getRoleName)),
                option("ONS configuration", safeString(dataSource::getONSConfiguration)),
                option("PDB roles", PoolInfoSupport.displayProperties(dataSource.getPdbRoles()))
            ),
            PoolInfoSupport.group(
                "Sizing",
                option("Initial pool size", safeInt(dataSource::getInitialPoolSize)),
                option("Minimum pool size", safeInt(dataSource::getMinPoolSize)),
                option("Minimum idle", safeInt(dataSource::getMinIdle)),
                option("Maximum pool size", safeInt(dataSource::getMaxPoolSize)),
                option("Max statements", safeInt(dataSource::getMaxStatements)),
                option("Max per service", dataSource.getMaxConnectionsPerService()),
                option("Max per shard", safeInt(dataSource::getMaxConnectionsPerShard))
            ),
            PoolInfoSupport.group(
                "Timeouts",
                option("Connection wait", PoolInfoSupport.displayDuration(safeDuration(dataSource::getConnectionWaitDuration))),
                durationSeconds("Wait while service down", safeLong(dataSource::getConnectionWaitTimeoutWhileServiceDown)),
                durationSeconds("Inactive connection", safeInt(dataSource::getInactiveConnectionTimeout)),
                durationSeconds("Abandoned connection", safeInt(dataSource::getAbandonedConnectionTimeout)),
                durationSeconds("Time to live", safeInt(dataSource::getTimeToLiveConnectionTimeout)),
                durationSeconds("Timeout check interval", safeInt(dataSource::getTimeoutCheckInterval)),
                durationSeconds("Max idle time", safeInt(dataSource::getMaxIdleTime)),
                durationSeconds("Max reuse time", safeLong(dataSource::getMaxConnectionReuseTime)),
                durationSeconds("Trust idle connection", dataSource.getSecondsToTrustIdleConnection()),
                durationSeconds("Connection validation", safeInt(dataSource::getConnectionValidationTimeout)),
                durationSeconds("Query timeout", dataSource.getQueryTimeout()),
                durationSeconds("Login timeout", safeInt(dataSource::getLoginTimeout))
            ),
            PoolInfoSupport.group(
                "Validation",
                option("Validate on borrow", safeBoolean(dataSource::getValidateConnectionOnBorrow)),
                option("Validation SQL", safeString(dataSource::getSQLForValidateConnection)),
                option("Connection properties", PoolInfoSupport.displayProperties(dataSource.getConnectionProperties())),
                option("Connection factory properties", PoolInfoSupport.displayProperties(dataSource.getConnectionFactoryProperties()))
            ),
            PoolInfoSupport.group(
                "Behavior",
                option("Fast connection failover", safeBoolean(dataSource::getFastConnectionFailoverEnabled)),
                option("Fail fast on chunk unavailable", optionalBoolean(dataSource, "getFailFastOnChunkUnavailable")),
                option("Read-only instances", dataSource.isReadOnlyInstanceAllowed()),
                option("Create in borrow thread", dataSource.isCreateConnectionInBorrowThread()),
                option("Commit on return", dataSource.isCommitOnConnectionReturn()),
                option("Sharding mode", dataSource.getShardingMode()),
                option("Harvest trigger", safeInt(dataSource::getConnectionHarvestTriggerCount)),
                option("Harvest max count", safeInt(dataSource::getConnectionHarvestMaxCount)),
                option("Max reuse count", safeInt(dataSource::getMaxConnectionReuseCount)),
                option("Property cycle", safeInt(dataSource::getPropertyCycle)),
                option("Connection labeling high cost", dataSource.getConnectionLabelingHighCost()),
                option("High cost reuse threshold", dataSource.getHighCostConnectionReuseThreshold()),
                option("Connection repurpose threshold", dataSource.getConnectionRepurposeThreshold())
            ),
            PoolInfoSupport.group(
                "Integrations",
                option("Event listener provider", dataSource.getUCPEventListenerProvider()),
                option("Initialization callback", safeType(dataSource::getConnectionInitializationCallback)),
                option("Creation consumer", safeType(dataSource::getConnectionCreationConsumer))
            ),
            PoolInfoSupport.group(
                "Statistics",
                option("Pending requests", statInt(stats, UniversalConnectionPoolStatistics::getPendingRequestsCount)),
                option("Peak connections", statInt(stats, UniversalConnectionPoolStatistics::getPeakConnectionsCount)),
                option("Peak borrowed", statInt(stats, UniversalConnectionPoolStatistics::getPeakBorrowedConnectionsCount)),
                option("Average borrowed", statInt(stats, UniversalConnectionPoolStatistics::getAverageBorrowedConnectionsCount)),
                option("Remaining capacity", statInt(stats, UniversalConnectionPoolStatistics::getRemainingPoolCapacityCount)),
                option("Labeled connections", statInt(stats, UniversalConnectionPoolStatistics::getLabeledConnectionsCount)),
                option("Created connections", statInt(stats, UniversalConnectionPoolStatistics::getConnectionsCreatedCount)),
                option("Closed connections", statInt(stats, UniversalConnectionPoolStatistics::getConnectionsClosedCount)),
                option("Abandoned connections", statInt(stats, UniversalConnectionPoolStatistics::getAbandonedConnectionsCount)),
                durationMillis("Average wait", statLong(stats, UniversalConnectionPoolStatistics::getAverageConnectionWaitTime)),
                durationMillis("Peak wait", statLong(stats, UniversalConnectionPoolStatistics::getPeakConnectionWaitTime)),
                option("Shard stats", statKeys(stats, UniversalConnectionPoolStatistics::getShardConnectionStats))
            ),
            PoolInfoSupport.group(
                "Cumulative statistics",
                option("Borrowed connections", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeConnectionBorrowedCount)),
                option("Returned connections", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeConnectionReturnedCount)),
                option("Successful waits", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeSuccessfulConnectionWaitCount)),
                option("Failed waits", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeFailedConnectionWaitCount)),
                option("Creation attempts", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeConnectionCreationAttemts)),
                option("Creation attempts since outage", statLong(stats, UniversalConnectionPoolStatistics::getConnectionCreationAttemptsSinceLastOutage)),
                durationMillis("Connection wait", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeConnectionWaitTime)),
                durationMillis("Connection use", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeConnectionUseTime)),
                durationMillis("Successful wait", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeSuccessfulConnectionWaitTime)),
                durationMillis("Failed wait", statLong(stats, UniversalConnectionPoolStatistics::getCumulativeFailedConnectionWaitTime))
            )
        );
    }

    private static String optionalBoolean(Object target, String methodName) {
        try {
            return PoolInfoSupport.display(target.getClass().getMethod(methodName).invoke(target));
        } catch (ReflectiveOperationException | RuntimeException _) {
            return PoolInfoSupport.UNKNOWN;
        }
    }

    private static String safeType(Supplier<Object> supplier) {
        try {
            return PoolInfoSupport.displayType(supplier.get());
        } catch (NoSuchMethodError _) {
            return PoolInfoSupport.UNKNOWN;
        }
    }

    private static int statInt(UniversalConnectionPoolStatistics stats, StatIntSupplier supplier) {
        if (stats == null) {
            return 0;
        }
        try {
            return supplier.getAsInt(stats);
        } catch (NoSuchMethodError _) {
            return 0;
        }
    }

    private static long statLong(UniversalConnectionPoolStatistics stats, StatLongSupplier supplier) {
        if (stats == null) {
            return 0;
        }
        try {
            return supplier.getAsLong(stats);
        } catch (NoSuchMethodError _) {
            return 0;
        }
    }

    private static String statKeys(UniversalConnectionPoolStatistics stats, StatMapSupplier supplier) {
        if (stats == null) {
            return PoolInfoSupport.UNKNOWN;
        }
        try {
            return PoolInfoSupport.displayKeys(supplier.get(stats));
        } catch (NoSuchMethodError _) {
            return PoolInfoSupport.UNKNOWN;
        }
    }

    @FunctionalInterface
    private interface StatIntSupplier {
        int getAsInt(UniversalConnectionPoolStatistics stats);
    }

    @FunctionalInterface
    private interface StatLongSupplier {
        long getAsLong(UniversalConnectionPoolStatistics stats);
    }

    @FunctionalInterface
    private interface StatMapSupplier {
        Map<String, ShardConnectionStatistics> get(UniversalConnectionPoolStatistics stats);
    }
}
