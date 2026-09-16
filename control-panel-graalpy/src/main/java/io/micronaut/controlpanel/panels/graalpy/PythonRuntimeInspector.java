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
package io.micronaut.controlpanel.panels.graalpy;

import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.python.GraalPyContextConfiguration;
import io.micronaut.context.python.GraalPyContextCustomizer;
import io.micronaut.context.python.PythonAsyncioConfiguration;
import io.micronaut.context.python.PythonConfiguration;
import io.micronaut.context.python.PythonContextExecutor;
import io.micronaut.context.python.PythonPoolConfiguration;
import io.micronaut.context.python.PythonPoolStatistics;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.value.PropertyResolver;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Engine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Read-only inspector for the Micronaut Core Python runtime.
 *
 * <p>Every lookup here is metadata only: the inspector never borrows a pooled context, never evaluates Python,
 * never instantiates a {@link GraalPyContextCustomizer}, and never creates a polyglot {@code Engine} or
 * {@code Context}.</p>
 */
@Singleton
public final class PythonRuntimeInspector {

    /**
     * Netty asyncio runtime class used to detect {@code micronaut-context-python-netty}.
     */
    static final String NETTY_EVENT_LOOP_CUSTOMIZER = "io.micronaut.context.python.netty.NettyPythonEventLoopServerCustomizer";
    /**
     * Management endpoint exposed by Core for pool statistics.
     */
    static final String POOL_ENDPOINT_CLASS = "io.micronaut.context.python.PythonPoolEndpoint";
    /**
     * Property prefix holding the GraalPy context environment.
     */
    static final String ENVIRONMENT_PREFIX = GraalPyContextConfiguration.PREFIX + ".environment";

    private static final Logger LOG = LoggerFactory.getLogger(PythonRuntimeInspector.class);
    private static final String MASK = "••••••";
    private static final List<String> SENSITIVE_FRAGMENTS = List.of("token", "secret", "password", "key", "credential");
    private static final String NOT_CONFIGURED = "Not configured";

    private final BeanContext beanContext;
    private final PythonContextExecutor executor;

    PythonRuntimeInspector(BeanContext beanContext, PythonContextExecutor executor) {
        this.beanContext = beanContext;
        this.executor = executor;
    }

    /**
     * @return the current context pool view
     */
    // S2583: PythonContextExecutor is a @FunctionalInterface whose statistics() default method never
    // returns null, so static analysis reads the check below as dead. Any implementation may override
    // it, including ones outside Core, and the panel must degrade instead of throwing on a null result.
    @SuppressWarnings("java:S2583")
    ContextPool contextPool() {
        PoolSettings settings = poolSettings();
        EngineInfo engine = engineInfo();
        PythonPoolStatistics snapshot;
        try {
            snapshot = executor.statistics();
        } catch (RuntimeException e) {
            LOG.debug("Unable to read GraalPy context pool statistics", e);
            return new ContextPool("Unavailable", false,
                "The Python context executor is present but did not report pool statistics.",
                null, settings, engine, List.of());
        }
        if (snapshot == null) {
            return new ContextPool("Unavailable", false,
                "The Python context executor reported no pool statistics.",
                null, settings, engine, List.of());
        }
        PoolStatistics statistics = new PoolStatistics(
            snapshot.enabled(),
            snapshot.targetSize(),
            snapshot.pooledContexts(),
            snapshot.idleContexts(),
            snapshot.eventLoopContexts(),
            snapshot.borrows(),
            snapshot.waits(),
            snapshot.totalWaitMillis(),
            snapshot.maxWaitMillis(),
            snapshot.closed());
        return new ContextPool(state(statistics), true, stateMessage(statistics), statistics, settings, engine, hints(statistics));
    }

    /**
     * @return the current context configuration view
     */
    ContextConfiguration contextConfiguration() {
        return beanContext.findBean(GraalPyContextConfiguration.class)
            .map(configuration -> new ContextConfiguration(
                true,
                options(configuration.getOptions()),
                List.copyOf(configuration.getHostClassLookup()),
                environmentKeys(),
                customizers()))
            .orElseGet(() -> new ContextConfiguration(false, List.of(), List.of(), environmentKeys(), customizers()));
    }

    /**
     * @return which optional Python runtime modules are present
     */
    RuntimeModules runtimeModules() {
        ClassLoader classLoader = beanContext.getClassLoader();
        return new RuntimeModules(
            ClassUtils.isPresent(NETTY_EVENT_LOOP_CUSTOMIZER, classLoader),
            ClassUtils.isPresent(POOL_ENDPOINT_CLASS, classLoader) && poolEndpointDefined());
    }

    private boolean poolEndpointDefined() {
        return beanContext.getAllBeanDefinitions()
            .stream()
            .anyMatch(definition -> POOL_ENDPOINT_CLASS.equals(definition.getBeanType().getName()));
    }

    private static String state(PoolStatistics statistics) {
        if (statistics.closed()) {
            return "Closed";
        }
        if (!statistics.enabled()) {
            return "Disabled";
        }
        return "Pooled " + statistics.pooledContexts() + "/" + statistics.targetSize();
    }

    private static String stateMessage(PoolStatistics statistics) {
        if (statistics.closed()) {
            return "The context pool is closed. Pooled contexts are no longer served.";
        }
        if (!statistics.enabled()) {
            return "Context pooling is disabled. Python runs on the single primary context.";
        }
        return "";
    }

    private static List<String> hints(PoolStatistics statistics) {
        List<String> hints = new ArrayList<>();
        if (statistics.enabled() && !statistics.closed()) {
            if (statistics.waits() > 0) {
                hints.add("Borrowers waited " + statistics.waits() + " time(s) for a context, the longest for "
                    + statistics.maxWaitMillis() + " ms. Waits that keep growing towards the configured warn-wait "
                    + "usually mean the pool is too small.");
            }
            if (statistics.pooledContexts() > 0 && statistics.idleContexts() == statistics.pooledContexts()) {
                hints.add("Every pooled context is idle, so the pool is larger than this workload needs.");
            }
        }
        return List.copyOf(hints);
    }

    private PoolSettings poolSettings() {
        String poolEnabled = NOT_CONFIGURED;
        String size = NOT_CONFIGURED;
        String warnWait = NOT_CONFIGURED;
        String maxEventLoopContexts = NOT_CONFIGURED;
        PythonPoolConfiguration poolConfiguration = beanContext.findBean(PythonPoolConfiguration.class).orElse(null);
        if (poolConfiguration != null) {
            poolEnabled = String.valueOf(poolConfiguration.enabled());
            size = poolConfiguration.size() == 0
                ? "0 (defaults to 2 x available processors)"
                : String.valueOf(poolConfiguration.size());
            warnWait = String.valueOf(poolConfiguration.warnWait());
            maxEventLoopContexts = String.valueOf(poolConfiguration.maxEventLoopContexts());
        }
        String asyncioEnabled = beanContext.findBean(PythonAsyncioConfiguration.class)
            .map(configuration -> String.valueOf(configuration.enabled()))
            .orElse(NOT_CONFIGURED);
        String pythonEnabled = beanContext.findBean(PythonConfiguration.class)
            .map(configuration -> String.valueOf(configuration.enabled()))
            .orElse(NOT_CONFIGURED);
        return new PoolSettings(poolEnabled, size, warnWait, maxEventLoopContexts, asyncioEnabled, pythonEnabled);
    }

    /**
     * Reads engine metadata only when a polyglot {@code Engine} bean has already been created by Core.
     *
     * @return the engine view
     */
    private EngineInfo engineInfo() {
        if (!(beanContext instanceof DefaultBeanContext defaultBeanContext)) {
            return EngineInfo.unknown();
        }
        try {
            return defaultBeanContext.getActiveBeanRegistrations(Engine.class)
                .stream()
                .map(registration -> new EngineInfo(true, registration.bean().getImplementationName(), registration.bean().getVersion()))
                .findFirst()
                .orElseGet(EngineInfo::unknown);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read GraalPy engine metadata", e);
            return EngineInfo.unknown();
        }
    }

    private static List<ConfigurationEntry> options(Map<String, String> options) {
        return options.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static ConfigurationEntry entry(String key, String value) {
        boolean sensitive = isSensitive(key);
        return new ConfigurationEntry(key, sensitive ? MASK : value, sensitive);
    }

    private static boolean isSensitive(String key) {
        String lowerCase = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_FRAGMENTS.stream().anyMatch(lowerCase::contains);
    }

    /**
     * Reads the environment variable names only. Values are never rendered because they routinely carry secrets.
     *
     * @return the configured environment variable names, sorted
     */
    private List<String> environmentKeys() {
        if (!(beanContext instanceof PropertyResolver propertyResolver)) {
            return List.of();
        }
        try {
            return propertyResolver.getProperties(ENVIRONMENT_PREFIX)
                .keySet()
                .stream()
                .sorted()
                .toList();
        } catch (RuntimeException e) {
            LOG.debug("Unable to read GraalPy context environment keys", e);
            return List.of();
        }
    }

    /**
     * Lists customizer implementation types without calling {@code Provider.get()}.
     *
     * @return the registered customizer class names, sorted
     */
    private List<String> customizers() {
        try {
            return ServiceLoader.load(GraalPyContextCustomizer.class, beanContext.getClassLoader())
                .stream()
                .map(provider -> provider.type().getName())
                .sorted()
                .toList();
        } catch (ServiceConfigurationError | RuntimeException e) {
            LOG.debug("Unable to list GraalPy context customizers", e);
            return List.of();
        }
    }

    /**
     * Context pool view.
     *
     * @param state short pool state label
     * @param available whether pool statistics could be read
     * @param message explanation shown for non-nominal states
     * @param statistics the pool statistics snapshot, {@code null} when unavailable
     * @param settings the pool and Python configuration values
     * @param engine polyglot engine metadata
     * @param hints reading hints derived from the snapshot
     */
    @ReflectiveAccess
    public record ContextPool(
        String state,
        boolean available,
        String message,
        PoolStatistics statistics,
        PoolSettings settings,
        EngineInfo engine,
        List<String> hints) {

        /**
         * @return whether an explanation message is present
         */
        public boolean hasMessage() {
            return message != null && !message.isBlank();
        }

        /**
         * @return whether reading hints are present
         */
        public boolean hasHints() {
            return !hints.isEmpty();
        }
    }

    /**
     * Snapshot of {@code PythonPoolStatistics}.
     *
     * @param enabled whether pooling is enabled
     * @param targetSize configured target pool size
     * @param pooledContexts contexts currently pooled
     * @param idleContexts pooled contexts currently idle
     * @param eventLoopContexts contexts bound to an event loop
     * @param borrows total borrow count
     * @param waits total borrow waits
     * @param totalWaitMillis total wait time in milliseconds
     * @param maxWaitMillis longest single wait in milliseconds
     * @param closed whether the pool is closed
     */
    @ReflectiveAccess
    public record PoolStatistics(
        boolean enabled,
        int targetSize,
        int pooledContexts,
        int idleContexts,
        int eventLoopContexts,
        long borrows,
        long waits,
        long totalWaitMillis,
        long maxWaitMillis,
        boolean closed) {
    }

    /**
     * Pool and Python configuration values.
     *
     * @param poolEnabled {@code micronaut.python.pool.enabled}
     * @param size {@code micronaut.python.pool.size}
     * @param warnWait {@code micronaut.python.pool.warn-wait}
     * @param maxEventLoopContexts {@code micronaut.python.pool.max-event-loop-contexts}
     * @param asyncioEnabled {@code micronaut.python.asyncio.enabled}
     * @param pythonEnabled {@code micronaut.python.enabled}
     */
    @ReflectiveAccess
    public record PoolSettings(
        String poolEnabled,
        String size,
        String warnWait,
        String maxEventLoopContexts,
        String asyncioEnabled,
        String pythonEnabled) {
    }

    /**
     * Polyglot engine metadata read from an already-created engine bean.
     *
     * @param available whether an engine instance already existed
     * @param implementationName engine implementation name
     * @param version engine version
     */
    @ReflectiveAccess
    public record EngineInfo(boolean available, String implementationName, String version) {

        private static EngineInfo unknown() {
            return new EngineInfo(false, "Unknown", "Unknown");
        }
    }

    /**
     * GraalPy context configuration view.
     *
     * @param available whether the context configuration bean is present
     * @param options {@code graalpy.context.options} entries with sensitive values masked
     * @param hostClassLookup {@code graalpy.context.host-class-lookup} patterns
     * @param environmentKeys {@code graalpy.context.environment} keys, values never rendered
     * @param customizers registered {@code GraalPyContextCustomizer} class names
     */
    @ReflectiveAccess
    public record ContextConfiguration(
        boolean available,
        List<ConfigurationEntry> options,
        List<String> hostClassLookup,
        List<String> environmentKeys,
        List<String> customizers) {

        /**
         * @return whether context options are configured
         */
        public boolean hasOptions() {
            return !options.isEmpty();
        }

        /**
         * @return whether host class lookup patterns are configured
         */
        public boolean hasHostClassLookup() {
            return !hostClassLookup.isEmpty();
        }

        /**
         * @return whether environment variables are configured
         */
        public boolean hasEnvironmentKeys() {
            return !environmentKeys.isEmpty();
        }

        /**
         * @return whether context customizers are registered
         */
        public boolean hasCustomizers() {
            return !customizers.isEmpty();
        }
    }

    /**
     * A single configuration key/value row.
     *
     * @param key configuration key
     * @param value configuration value, masked when sensitive
     * @param masked whether the value was masked
     */
    @ReflectiveAccess
    public record ConfigurationEntry(String key, String value, boolean masked) {
    }

    /**
     * Optional Python runtime modules detected on the classpath.
     *
     * @param nettyPresent whether {@code micronaut-context-python-netty} is present
     * @param poolEndpointPresent whether Core's {@code /pythonpool} endpoint bean is defined
     */
    @ReflectiveAccess
    public record RuntimeModules(boolean nettyPresent, boolean poolEndpointPresent) {
    }
}
