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

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.r2dbc.config.R2dbcHealthConfiguration;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.PoolMetrics;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class R2dbcDiagnosticsServiceTest {

    @Test
    void buildsSanitizedConnectionSummaryFromOptions() {
        var service = service(
            "default",
            connectionFactory("PostgreSQL"),
            options("postgresql", "postgresql", "localhost", 5432, "orders", "dbuser")
                .option(ConnectionFactoryOptions.PASSWORD, "secret")
                .build(),
            Map.of("password", "secret", "api-token", "token", "schema", "public"),
            Map.of()
        );

        var summary = service.connectionSummary();

        assertEquals("default", summary.name());
        assertEquals("postgresql", summary.driver());
        assertEquals("localhost", summary.host());
        assertEquals("5432", summary.port());
        assertEquals("orders", summary.database());
        assertEquals("dbuser", summary.user());
        assertEquals("PostgreSQL", summary.driverMetadataName());
        assertTrue(summary.options().stream().anyMatch(option -> option.name().equals("schema")));
        assertFalse(summary.options().stream().anyMatch(option -> option.name().contains("password")));
        assertFalse(summary.options().stream().anyMatch(option -> option.name().contains("token")));
    }

    @Test
    void masksCredentialBearingR2dbcUrlsInDisplayedOptions() {
        var service = service(
            "default",
            connectionFactory("PostgreSQL"),
            null,
            Map.of(
                "url", "r2dbc:postgresql://user:p@ss@localhost/db",
                "pool-url", "r2dbc:pool:postgresql://pool-user:pool-secret@localhost/pooldb"
            ),
            Map.of()
        );

        var options = service.connectionSummary().options();

        assertTrue(options.stream().anyMatch(option -> option.name().equals("url") && option.value().equals("r2dbc:postgresql://***@localhost/db")));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("pool-url") && option.value().equals("r2dbc:pool:postgresql://***@localhost/pooldb")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("secret")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("p@ss")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("ss@")));
    }

    @Test
    void masksCredentialBearingQueryParametersInDisplayedUrlOptions() {
        var service = service(
            "default",
            connectionFactory("PostgreSQL"),
            null,
            Map.of(
                "url", "r2dbc:postgresql://localhost/db?user=app&password=secret&ssl=true"
            ),
            Map.of(
                "pool-url", "r2dbc:pool:postgresql://localhost/pooldb?token=secret-token&authorization=Bearer%20secret&max-size=10"
            )
        );

        var options = service.connectionSummary().options();
        var poolOptions = service.poolDiagnostics().configuration();

        assertTrue(options.stream().anyMatch(option -> option.name().equals("url") && option.value().equals("r2dbc:postgresql://localhost/db?user=app&password=***&ssl=true")));
        assertTrue(poolOptions.stream().anyMatch(option -> option.name().equals("pool-url") && option.value().equals("r2dbc:pool:postgresql://localhost/pooldb?token=***&authorization=***&max-size=10")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("secret")));
        assertFalse(poolOptions.stream().anyMatch(option -> option.value().contains("secret-token")));
        assertFalse(poolOptions.stream().anyMatch(option -> option.value().contains("Bearer")));
    }

    @Test
    void showsOptionValuesOnlyForAllowedSafeKeys() {
        var service = service(
            "default",
            connectionFactory("PostgreSQL"),
            null,
            Map.of(
                "schema", "public",
                "authorization", "Bearer hidden-token",
                "auth", "hidden-auth",
                "sasl.jaas.config", "username=\"app\" password=\"hidden-password\""
            ),
            Map.of()
        );

        var options = service.connectionSummary().options();

        assertTrue(options.stream().anyMatch(option -> option.name().equals("schema") && option.value().equals("public") && !option.redacted()));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("authorization") && option.redacted()));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("auth") && option.redacted()));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("sasl.jaas.config") && option.redacted()));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("hidden-token")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("hidden-auth")));
        assertFalse(options.stream().anyMatch(option -> option.value().contains("hidden-password")));
    }

    @Test
    void deduplicatesOptionsByName() {
        var service = service(
            "default",
            connectionFactory("H2"),
            options("postgresql", "postgresql", "localhost", 5432, "", "dbuser").build(),
            Map.of("database", "configured-orders"),
            Map.of()
        );

        var databaseOptions = service.connectionSummary()
            .options()
            .stream()
            .filter(option -> option.name().equals("database"))
            .toList();

        assertEquals(1, databaseOptions.size());
        assertEquals("configured-orders", databaseOptions.getFirst().value());
    }

    @Test
    void buildsBodyWithWarningsForPartialDiagnostics() {
        R2dbcHealthConfiguration healthConfiguration = mock(R2dbcHealthConfiguration.class);
        when(healthConfiguration.isEnabled()).thenReturn(true);
        when(healthConfiguration.getHealthQuery("")).thenReturn(Optional.empty());
        var service = service("default", connectionFactory(""), null, Map.of(), Map.of(), healthConfiguration, panelConfiguration());

        var body = service.getBody();

        assertEquals("", body.summary().driverMetadataName());
        assertEquals("NO_QUERY", body.health().status());
        assertEquals("PARTIAL", body.pool().status());
        assertTrue(body.warnings().contains("Only partial connection metadata is available for this factory."));
        assertTrue(body.warnings().contains("Health validation is not configured for the reported driver metadata name."));
        assertTrue(body.warnings().contains("Pool diagnostics are partial because portable R2DBC pool metrics are unavailable."));
    }

    @Test
    void healthValidationCanBeDisabledByPanelOrHealthConfiguration() {
        R2dbcPanelConfiguration panelConfiguration = panelConfiguration();
        panelConfiguration.setHealthEnabled(false);
        var panelDisabled = service("default", connectionFactory("H2"), null, Map.of(), Map.of(), healthConfiguration(), panelConfiguration);

        assertEquals("DISABLED", panelDisabled.health("H2").status());
        assertEquals("R2DBC validation is disabled for the Control Panel.", panelDisabled.health("H2").message());

        R2dbcHealthConfiguration healthConfiguration = mock(R2dbcHealthConfiguration.class);
        when(healthConfiguration.isEnabled()).thenReturn(false);
        var healthDisabled = service("default", connectionFactory("H2"), null, Map.of(), Map.of(), healthConfiguration, panelConfiguration());

        assertEquals("DISABLED", healthDisabled.health("H2").status());
        assertEquals("The Micronaut R2DBC health endpoint is disabled.", healthDisabled.health("H2").message());
    }

    @Test
    void healthValidationReportsUpWhenConfiguredQuerySucceeds() {
        ConnectionFactory connectionFactory = connectionFactory("H2");
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        doReturn(Mono.just(connection)).when(connectionFactory).create();
        when(connection.createStatement("SELECT 1")).thenReturn(statement);
        doReturn(Mono.just(mock(Result.class))).when(statement).execute();
        when(connection.close()).thenReturn(Mono.empty());

        var service = service("default", connectionFactory, null, Map.of(), Map.of());

        assertEquals("UP", service.health("H2").status());
    }

    @Test
    void healthValidationReportsDownWhenQueryFails() {
        ConnectionFactory connectionFactory = connectionFactory("H2");
        when(connectionFactory.create()).thenReturn(Mono.error(new RuntimeException(new IllegalStateException("r2dbc://user:secret@localhost/db"))));
        var service = service("default", connectionFactory, null, Map.of(), Map.of());

        var health = service.health("H2");

        assertEquals("DOWN", health.status());
        assertEquals("IllegalStateException", health.errorType());
        assertEquals("Validation failed.", health.message());
    }

    @Test
    void healthValidationReportsTimeout() {
        ConnectionFactory connectionFactory = connectionFactory("H2");
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        doReturn(Mono.just(connection)).when(connectionFactory).create();
        when(connection.createStatement("SELECT 1")).thenReturn(statement);
        doReturn(Mono.never()).when(statement).execute();
        when(connection.close()).thenReturn(Mono.empty());
        R2dbcPanelConfiguration panelConfiguration = panelConfiguration();
        panelConfiguration.setHealthTimeout(Duration.ofMillis(1));

        var service = service("default", connectionFactory, null, Map.of(), Map.of(), healthConfiguration(), panelConfiguration);

        assertEquals("TIMEOUT", service.health("H2").status());
    }

    @Test
    void healthValidationReportsNoQueryForUnsupportedDriver() {
        R2dbcHealthConfiguration healthConfiguration = mock(R2dbcHealthConfiguration.class);
        when(healthConfiguration.isEnabled()).thenReturn(true);
        when(healthConfiguration.getHealthQuery("Unsupported")).thenReturn(Optional.empty());
        var service = service("default", connectionFactory("Unsupported"), null, Map.of(), Map.of(), healthConfiguration, panelConfiguration());

        assertEquals("NO_QUERY", service.health("Unsupported").status());
    }

    @Test
    void poolDiagnosticsExposeMetricsWhenConnectionFactoryIsPool() {
        ConnectionPool pool = mock(ConnectionPool.class);
        PoolMetrics metrics = mock(PoolMetrics.class);
        when(pool.getMetrics()).thenReturn(Optional.of(metrics));
        when(metrics.acquiredSize()).thenReturn(1);
        when(metrics.allocatedSize()).thenReturn(2);
        when(metrics.idleSize()).thenReturn(3);
        when(metrics.pendingAcquireSize()).thenReturn(4);
        when(metrics.getMaxAllocatedSize()).thenReturn(10);
        when(metrics.getMaxPendingAcquireSize()).thenReturn(20);
        when(pool.getMetadata()).thenReturn(() -> "H2");

        var service = service("default", pool, null, Map.of(), Map.of("max-size", "10"));

        var diagnostics = service.poolDiagnostics();

        assertEquals("DETECTED", diagnostics.status());
        assertEquals(6, diagnostics.metrics().size());
        assertEquals("10", diagnostics.configuration().getFirst().value());
    }

    @Test
    void poolDiagnosticsHandleUnavailableAndUnreadableMetrics() {
        ConnectionPool unavailable = mock(ConnectionPool.class);
        when(unavailable.getMetrics()).thenReturn(Optional.empty());
        when(unavailable.getMetadata()).thenReturn(() -> "H2");
        var unavailableService = service("default", unavailable, null, Map.of(), Map.of());

        assertEquals("DETECTED", unavailableService.poolDiagnostics().status());
        assertTrue(unavailableService.poolDiagnostics().metrics().isEmpty());

        ConnectionPool unreadable = mock(ConnectionPool.class);
        doReturn(Optional.of("not metrics")).when(unreadable).getMetrics();
        when(unreadable.getMetadata()).thenReturn(() -> "H2");
        var unreadableService = service("default", unreadable, null, Map.of(), Map.of());

        assertEquals("PARTIAL", unreadableService.poolDiagnostics().status());
        assertEquals("The connection factory is pooled, but pool metrics could not be read safely.", unreadableService.poolDiagnostics().message());
    }

    @Test
    void sanitizesHierarchicalUrlsAndSafeQueryStrings() {
        var service = service(
            "default",
            connectionFactory("PostgreSQL"),
            null,
            Map.of(
                "url", "https://user:secret@example.com:8443/db?password=secret#frag",
                "pool-url", "r2dbc:postgresql://localhost/db?ssl=true#frag",
                "schema", " "
            ),
            Map.of("pool-url", "r2dbc:pool:postgresql://localhost/db?token&max-size=10")
        );

        var options = service.connectionSummary().options();
        var poolOptions = service.poolDiagnostics().configuration();

        assertTrue(options.stream().anyMatch(option -> option.name().equals("url") && option.value().equals("https://***@example.com:8443/db?password=***#frag")));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("pool-url") && option.value().equals("r2dbc:postgresql://localhost/db?ssl=true#frag")));
        assertTrue(options.stream().anyMatch(option -> option.name().equals("schema") && option.value().isBlank()));
        assertTrue(poolOptions.stream().anyMatch(option -> option.name().equals("pool-url") && option.value().equals("r2dbc:pool:postgresql://localhost/db?token&max-size=10")));
    }

    @Test
    void panelConfigurationDefaultsAndSettersAreUsable() {
        R2dbcPanelConfiguration configuration = new R2dbcPanelConfiguration();

        assertEquals(Duration.ofSeconds(2), configuration.getHealthTimeout());
        assertTrue(configuration.isHealthEnabled());
        assertFalse(configuration.isShowOptionValues());

        configuration.setHealthTimeout(Duration.ofMillis(5));
        configuration.setHealthEnabled(false);
        configuration.setShowOptionValues(true);

        assertEquals(Duration.ofMillis(5), configuration.getHealthTimeout());
        assertFalse(configuration.isHealthEnabled());
        assertTrue(configuration.isShowOptionValues());
    }

    private static R2dbcDiagnosticsService service(String beanName,
                                                   ConnectionFactory connectionFactory,
                                                   ConnectionFactoryOptions options,
                                                   Map<String, Object> configuredOptions,
                                                   Map<String, Object> poolOptions) {
        return service(beanName, connectionFactory, options, configuredOptions, poolOptions, healthConfiguration(), panelConfiguration());
    }

    private static R2dbcDiagnosticsService service(String beanName,
                                                   ConnectionFactory connectionFactory,
                                                   ConnectionFactoryOptions options,
                                                   Map<String, Object> configuredOptions,
                                                   Map<String, Object> poolOptions,
                                                   R2dbcHealthConfiguration healthConfiguration,
                                                   R2dbcPanelConfiguration panelConfiguration) {
        BeanContext beanContext = mock(BeanContext.class);
        when(beanContext.findBean(eq(ConnectionFactoryOptions.class), any())).thenReturn(Optional.ofNullable(options));
        Environment environment = mock(Environment.class);
        when(environment.getProperties("r2dbc.datasources." + beanName + ".options", io.micronaut.core.naming.conventions.StringConvention.RAW)).thenReturn(configuredOptions);
        when(environment.getProperties("r2dbc.datasources." + beanName + ".pool", io.micronaut.core.naming.conventions.StringConvention.RAW)).thenReturn(poolOptions);
        when(environment.getProperty(anyString(), eq(Object.class))).thenReturn(Optional.empty());
        return new R2dbcDiagnosticsService(beanName, connectionFactory, beanContext, environment, healthConfiguration, panelConfiguration);
    }

    private static R2dbcHealthConfiguration healthConfiguration() {
        R2dbcHealthConfiguration configuration = mock(R2dbcHealthConfiguration.class);
        when(configuration.isEnabled()).thenReturn(true);
        when(configuration.getHealthQuery(anyString())).thenReturn(Optional.of("SELECT 1"));
        return configuration;
    }

    private static R2dbcPanelConfiguration panelConfiguration() {
        R2dbcPanelConfiguration configuration = new R2dbcPanelConfiguration();
        configuration.setHealthTimeout(Duration.ofMillis(250));
        configuration.setShowOptionValues(true);
        return configuration;
    }

    private static ConnectionFactory connectionFactory(String metadataName) {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        ConnectionFactoryMetadata metadata = () -> metadataName;
        when(factory.getMetadata()).thenReturn(metadata);
        return factory;
    }

    private static ConnectionFactoryOptions.Builder options(String driver,
                                                            String protocol,
                                                            String host,
                                                            int port,
                                                            String database,
                                                            String user) {
        return ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, driver)
            .option(ConnectionFactoryOptions.PROTOCOL, protocol)
            .option(ConnectionFactoryOptions.HOST, host)
            .option(ConnectionFactoryOptions.PORT, port)
            .option(ConnectionFactoryOptions.DATABASE, database)
            .option(ConnectionFactoryOptions.USER, user);
    }
}
