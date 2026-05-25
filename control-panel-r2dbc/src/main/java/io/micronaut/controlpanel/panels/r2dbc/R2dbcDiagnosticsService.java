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
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.r2dbc.BasicR2dbcProperties;
import io.micronaut.r2dbc.config.R2dbcHealthConfiguration;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcBody;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcConnectionSummary;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcHealth;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcOption;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcPoolDiagnostics;
import io.micronaut.controlpanel.panels.r2dbc.model.R2dbcPoolMetric;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Builds safe diagnostics for a single R2DBC {@link ConnectionFactory}.
 *
 * @author Sergio del Amo
 * @since 2.0.0
 */
@EachBean(ConnectionFactory.class)
public final class R2dbcDiagnosticsService {

    private static final String POOL_CLASS = "io.r2dbc.pool.ConnectionPool";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final List<Option<?>> STANDARD_OPTIONS = List.of(
        ConnectionFactoryOptions.DRIVER,
        ConnectionFactoryOptions.PROTOCOL,
        ConnectionFactoryOptions.HOST,
        ConnectionFactoryOptions.PORT,
        ConnectionFactoryOptions.DATABASE,
        ConnectionFactoryOptions.USER,
        ConnectionFactoryOptions.SSL,
        ConnectionFactoryOptions.CONNECT_TIMEOUT,
        ConnectionFactoryOptions.LOCK_WAIT_TIMEOUT,
        ConnectionFactoryOptions.STATEMENT_TIMEOUT
    );
    private static final Set<String> SAFE_OPTION_VALUE_NAMES = Set.of(
        "application-name",
        "connect-timeout",
        "database",
        "driver",
        "host",
        "initial-size",
        "lock-wait-timeout",
        "max-acquire-time",
        "max-create-connection-time",
        "max-idle-time",
        "max-life-time",
        "max-size",
        "name",
        "pool-url",
        "port",
        "protocol",
        "schema",
        "ssl",
        "ssl-mode",
        "statement-timeout",
        "url",
        "user",
        "username",
        "validation-query"
    );

    private final String beanName;
    private final ConnectionFactory connectionFactory;
    private final BeanContext beanContext;
    private final Environment environment;
    private final R2dbcHealthConfiguration healthConfiguration;
    private final R2dbcPanelConfiguration panelConfiguration;

    /**
     * Creates an R2DBC diagnostics service for one connection factory.
     *
     * @param beanName the connection factory bean name
     * @param connectionFactory the connection factory
     * @param beanContext the bean context
     * @param environment the environment
     * @param healthConfiguration the R2DBC health configuration
     * @param panelConfiguration the R2DBC panel configuration
     */
    public R2dbcDiagnosticsService(@Parameter String beanName,
                                   @Parameter ConnectionFactory connectionFactory,
                                   BeanContext beanContext,
                                   Environment environment,
                                   R2dbcHealthConfiguration healthConfiguration,
                                   R2dbcPanelConfiguration panelConfiguration) {
        this.beanName = beanName;
        this.connectionFactory = connectionFactory;
        this.beanContext = beanContext;
        this.environment = environment;
        this.healthConfiguration = healthConfiguration;
        this.panelConfiguration = panelConfiguration;
    }

    /**
     * The bean name backing this diagnostics service.
     *
     * @return the bean name backing this diagnostics service
     */
    public String getBeanName() {
        return beanName;
    }

    /**
     * Builds the current diagnostics body.
     *
     * @return a fresh diagnostics body
     */
    public R2dbcBody getBody() {
        R2dbcConnectionSummary summary = connectionSummary();
        R2dbcHealth health = health(summary.driverMetadataName());
        R2dbcPoolDiagnostics pool = poolDiagnostics();
        List<String> warnings = warnings(summary, health, pool);
        return new R2dbcBody(summary, health, pool, warnings);
    }

    R2dbcConnectionSummary connectionSummary() {
        Optional<ConnectionFactoryOptions> options = connectionFactoryOptions();
        String metadataName = safeValue(connectionFactory.getMetadata().getName());
        return new R2dbcConnectionSummary(
            beanName,
            option(options, ConnectionFactoryOptions.DRIVER, "driver"),
            option(options, ConnectionFactoryOptions.PROTOCOL, "protocol"),
            option(options, ConnectionFactoryOptions.HOST, "host"),
            option(options, ConnectionFactoryOptions.PORT, "port"),
            option(options, ConnectionFactoryOptions.DATABASE, "database"),
            option(options, ConnectionFactoryOptions.USER, "username"),
            option(options, ConnectionFactoryOptions.SSL, "ssl"),
            option(options, ConnectionFactoryOptions.CONNECT_TIMEOUT, "connect-timeout"),
            metadataName,
            optionList(options)
        );
    }

    R2dbcHealth health(String driverMetadataName) {
        if (!panelConfiguration.isHealthEnabled()) {
            return new R2dbcHealth("DISABLED", "Disabled", EMPTY_STRING, "R2DBC validation is disabled for the Control Panel.", EMPTY_STRING);
        }
        if (!healthConfiguration.isEnabled()) {
            return new R2dbcHealth("DISABLED", "Disabled", EMPTY_STRING, "The Micronaut R2DBC health endpoint is disabled.", EMPTY_STRING);
        }
        Optional<String> query = healthConfiguration.getHealthQuery(driverMetadataName);
        if (query.isEmpty()) {
            return new R2dbcHealth("NO_QUERY", "No query", EMPTY_STRING, "No validation query is configured for this R2DBC driver.", EMPTY_STRING);
        }
        try {
            Mono.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> executeValidationQuery(connection, query.get()),
                connection -> Mono.from(connection.close()),
                (connection, error) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close())
            ).timeout(panelConfiguration.getHealthTimeout()).block();
            return new R2dbcHealth("UP", "Up", query.get(), "Validation query completed.", EMPTY_STRING);
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            if (cause instanceof java.util.concurrent.TimeoutException) {
                return new R2dbcHealth("TIMEOUT", "Timeout", query.get(), "Validation timed out.", cause.getClass().getSimpleName());
            }
            return new R2dbcHealth("DOWN", "Down", query.get(), "Validation failed.", cause.getClass().getSimpleName());
        }
    }

    R2dbcPoolDiagnostics poolDiagnostics() {
        List<R2dbcOption> configuredPoolOptions = configuredPoolOptions();
        if (!isConnectionPool()) {
            String message = configuredPoolOptions.isEmpty()
                ? "Pool metadata is not available for this connection factory."
                : "Pool configuration was found, but runtime pool metrics are not exposed by this connection factory.";
            return new R2dbcPoolDiagnostics(STATUS_PARTIAL, "Partial support", message, List.of(), configuredPoolOptions);
        }
        return poolMetrics(configuredPoolOptions);
    }

    private R2dbcPoolDiagnostics poolMetrics(List<R2dbcOption> configuredPoolOptions) {
        try {
            Method getMetrics = connectionFactory.getClass().getMethod("getMetrics");
            Optional<?> metrics = (Optional<?>) getMetrics.invoke(connectionFactory);
            if (metrics.isEmpty()) {
                return new R2dbcPoolDiagnostics("DETECTED", "Pool detected", "The connection factory is pooled, but live metrics are unavailable.", List.of(), configuredPoolOptions);
            }
            Object value = metrics.get();
            return new R2dbcPoolDiagnostics("DETECTED", "Pool detected", "Live R2DBC pool metrics are available.", List.of(
                metric(value, "acquired", "acquiredSize"),
                metric(value, "allocated", "allocatedSize"),
                metric(value, "idle", "idleSize"),
                metric(value, "pending acquire", "pendingAcquireSize"),
                metric(value, "max allocated", "getMaxAllocatedSize"),
                metric(value, "max pending acquire", "getMaxPendingAcquireSize")
            ), configuredPoolOptions);
        } catch (ReflectiveOperationException | ClassCastException _) {
            return new R2dbcPoolDiagnostics(STATUS_PARTIAL, "Partial support", "The connection factory is pooled, but pool metrics could not be read safely.", List.of(), configuredPoolOptions);
        }
    }

    private boolean isConnectionPool() {
        try {
            return Class.forName(POOL_CLASS).isInstance(connectionFactory);
        } catch (ClassNotFoundException _) {
            return false;
        }
    }

    private static R2dbcPoolMetric metric(Object metrics, String name, String methodName) throws ReflectiveOperationException {
        return new R2dbcPoolMetric(name, String.valueOf(metrics.getClass().getMethod(methodName).invoke(metrics)));
    }

    private Mono<Void> executeValidationQuery(Connection connection, String query) {
        return Mono.from(connection.createStatement(query).execute()).then();
    }

    private Optional<ConnectionFactoryOptions> connectionFactoryOptions() {
        Optional<ConnectionFactoryOptions> options = beanContext.findBean(ConnectionFactoryOptions.class, Qualifiers.byName(beanName));
        if (options.isPresent()) {
            return options;
        }
        return beanContext.findBean(BasicR2dbcProperties.class, Qualifiers.byName(beanName))
            .map(properties -> properties.builder().build());
    }

    private String option(Optional<ConnectionFactoryOptions> options, Option<?> option, String propertyName) {
        return options.map(value -> value.getValue(option))
            .map(R2dbcDiagnosticsService::safeValue)
            .filter(value -> !value.isBlank())
            .orElseGet(() -> environment.getProperty(propertyPrefix() + "." + propertyName, Object.class)
                .map(R2dbcDiagnosticsService::safeValue)
                .orElse(EMPTY_STRING));
    }

    private List<R2dbcOption> optionList(Optional<ConnectionFactoryOptions> options) {
        List<R2dbcOption> optionList = new ArrayList<>();
        options.ifPresent(connectionFactoryOptions -> STANDARD_OPTIONS.stream()
            .filter(connectionFactoryOptions::hasOption)
            .map(option -> option(option.name(), connectionFactoryOptions.getValue(option)))
            .forEach(optionList::add));
        optionList.addAll(environment.getProperties(propertyPrefix() + ".options", StringConvention.RAW)
            .entrySet()
            .stream()
            .map(entry -> option(entry.getKey(), entry.getValue()))
            .toList());
        return deduplicateByName(optionList.stream()
            .filter(option -> !isSecretKey(option.name()))
            .toList());
    }

    private static List<R2dbcOption> deduplicateByName(List<R2dbcOption> options) {
        LinkedHashMap<String, R2dbcOption> byName = new LinkedHashMap<>();
        for (R2dbcOption option : options) {
            byName.merge(option.name(), option, R2dbcDiagnosticsService::preferredOption);
        }
        return byName.values()
            .stream()
            .sorted(Comparator.comparing(R2dbcOption::name))
            .toList();
    }

    private static R2dbcOption preferredOption(R2dbcOption current, R2dbcOption candidate) {
        if (current.redacted() && !candidate.redacted()) {
            return candidate;
        }
        if (current.value().isBlank() && !candidate.value().isBlank()) {
            return candidate;
        }
        return current;
    }

    private List<R2dbcOption> configuredPoolOptions() {
        return environment.getProperties(propertyPrefix() + ".pool", StringConvention.RAW)
            .entrySet()
            .stream()
            .filter(entry -> !isSecretKey(entry.getKey()))
            .map(entry -> option(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(R2dbcOption::name))
            .toList();
    }

    private R2dbcOption option(String key, Object value) {
        if (!panelConfiguration.isShowOptionValues() || isSecretKey(key) || !isSafeValueOptionName(key)) {
            return new R2dbcOption(key, EMPTY_STRING, true);
        }
        return new R2dbcOption(key, safeValue(value), false);
    }

    private static boolean isSecretKey(String key) {
        String normalized = key.toLowerCase(Locale.ENGLISH)
            .replace("-", EMPTY_STRING)
            .replace("_", EMPTY_STRING)
            .replace(".", EMPTY_STRING);
        return normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("accesskey")
            || normalized.contains("apikey")
            || normalized.contains("privatekey");
    }

    private static boolean isSafeValueOptionName(String key) {
        String normalized = key.toLowerCase(Locale.ENGLISH).replace('_', '-');
        return SAFE_OPTION_VALUE_NAMES.contains(normalized);
    }

    private List<String> warnings(R2dbcConnectionSummary summary, R2dbcHealth health, R2dbcPoolDiagnostics pool) {
        List<String> warnings = new ArrayList<>();
        if (summary.driver().isBlank() && summary.driverMetadataName().isBlank()) {
            warnings.add("Only partial connection metadata is available for this factory.");
        }
        if ("NO_QUERY".equals(health.status())) {
            warnings.add("Health validation is not configured for the reported driver metadata name.");
        } else if ("DOWN".equals(health.status()) || "TIMEOUT".equals(health.status())) {
            warnings.add("The connection factory is configured but currently unreachable by the validation probe.");
        }
        if (STATUS_PARTIAL.equals(pool.status())) {
            warnings.add("Pool diagnostics are partial because portable R2DBC pool metrics are unavailable.");
        }
        return warnings;
    }

    private String propertyPrefix() {
        return "r2dbc.datasources." + beanName;
    }

    private static String safeValue(Object value) {
        if (value == null) {
            return EMPTY_STRING;
        }
        String stringValue = String.valueOf(value);
        if (stringValue.isBlank()) {
            return EMPTY_STRING;
        }
        return sanitizeUrl(stringValue);
    }

    private static String sanitizeUrl(String value) {
        if (!value.contains("://")) {
            return value;
        }
        String masked = maskUrlUserInfo(value);
        return maskUrlQueryCredentials(masked);
    }

    private static String maskUrlUserInfo(String value) {
        if (!value.contains("@")) {
            return value;
        }
        try {
            URI uri = new URI(value);
            if (uri.getUserInfo() == null) {
                return maskUrlUserInfoByDelimiter(value);
            }
            String authority = "***@" + uri.getHost();
            if (uri.getPort() > -1) {
                authority += ":" + uri.getPort();
            }
            return new URI(uri.getScheme(), authority, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (URISyntaxException _) {
            return maskUrlUserInfoByDelimiter(value);
        }
    }

    private static String maskUrlQueryCredentials(String value) {
        int queryStart = value.indexOf('?');
        if (queryStart < 0) {
            return value;
        }
        int queryEnd = value.indexOf('#', queryStart);
        String query = queryEnd < 0 ? value.substring(queryStart + 1) : value.substring(queryStart + 1, queryEnd);
        String maskedQuery = maskQueryCredentials(query);
        if (maskedQuery.equals(query)) {
            return value;
        }
        String prefix = value.substring(0, queryStart + 1);
        String suffix = queryEnd < 0 ? EMPTY_STRING : value.substring(queryEnd);
        return prefix + maskedQuery + suffix;
    }

    private static String maskQueryCredentials(String query) {
        if (query.isEmpty()) {
            return query;
        }
        String[] parameters = query.split("&", -1);
        for (int i = 0; i < parameters.length; i++) {
            int equals = parameters[i].indexOf('=');
            String name = equals < 0 ? parameters[i] : parameters[i].substring(0, equals);
            if (isSecretQueryKey(name)) {
                parameters[i] = equals < 0 ? name : name + "=***";
            }
        }
        return String.join("&", parameters);
    }

    private static boolean isSecretQueryKey(String key) {
        String normalized = key.toLowerCase(Locale.ENGLISH);
        return normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("key")
            || normalized.contains("access")
            || normalized.contains("auth")
            || normalized.contains("authorization");
    }

    private static String maskUrlUserInfoByDelimiter(String value) {
        int scheme = value.indexOf("://");
        int authorityStart = scheme + 3;
        int authorityEnd = value.length();
        for (char delimiter : new char[] {'/', '?', '#'}) {
            int index = value.indexOf(delimiter, authorityStart);
            if (index > -1) {
                authorityEnd = Math.min(authorityEnd, index);
            }
        }
        int at = value.lastIndexOf('@', authorityEnd - 1);
        if (at < authorityStart) {
            return value;
        }
        return at > -1 ? value.substring(0, scheme + 3) + "***@" + value.substring(at + 1) : value;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && (result instanceof RuntimeException || result instanceof java.util.concurrent.ExecutionException)) {
            result = result.getCause();
        }
        return result;
    }
}
