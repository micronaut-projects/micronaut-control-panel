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
package io.micronaut.controlpanel.panels.tracing;

import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Builds read-only tracing diagnostics from local Micronaut state.
 */
@Singleton
@Internal
public final class TracingDiagnosticsService {

    private static final String NOT_CONFIGURED = "not configured";
    private static final String NONE = "none";
    private static final String PRESENT = "present";
    private static final String WARNING = "warning";
    private static final String RESOURCE_ATTRIBUTES_KEY = "otel.resource.attributes";
    private static final List<String> CONFIGURATION_KEYS = List.of(
            "otel.service.name",
            RESOURCE_ATTRIBUTES_KEY,
            "otel.traces.exporter",
            "otel.metrics.exporter",
            "otel.logs.exporter",
            "otel.exporter.otlp.endpoint",
            "otel.exporter.otlp.traces.endpoint",
            "otel.exporter.otlp.metrics.endpoint",
            "otel.exporter.otlp.logs.endpoint",
            "otel.exporter.otlp.headers",
            "otel.propagators",
            "otel.traces.sampler",
            "otel.traces.sampler.arg",
            "otel.sdk.disabled",
            "tracing.zipkin.enabled",
            "tracing.jaeger.enabled",
            "tracing.opentracing.enabled",
            "kafka.tracing.enabled",
            "kafka.tracing.producer.enabled",
            "kafka.tracing.consumer.enabled",
            "kafka.tracing.excluded-topics",
            "kafka.tracing.included-topics",
            "logger.levels.io.opentelemetry",
            "logger.levels.io.micronaut.tracing"
    );

    private static final List<ClassCheck> PROVIDERS = List.of(
            new ClassCheck("OpenTelemetry", "io.opentelemetry.api.OpenTelemetry", "OpenTelemetry API is on the classpath."),
            new ClassCheck("OpenTelemetry SDK", "io.opentelemetry.sdk.OpenTelemetrySdk", "OpenTelemetry SDK is on the classpath."),
            new ClassCheck("Brave/Zipkin", "brave.Tracing", "Brave tracing is on the classpath."),
            new ClassCheck("OpenTracing/Jaeger", "io.opentracing.Tracer", "OpenTracing API is on the classpath."),
            new ClassCheck("Jaeger", "io.jaegertracing.Configuration", "Jaeger client configuration is on the classpath.")
    );

    private static final List<InstrumentationCheck> INSTRUMENTATIONS = List.of(
            new InstrumentationCheck(
                    "HTTP client/server",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryClientFilter",
                            "io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter",
                            "io.micronaut.tracing.brave.http.BraveTracingClientFilter",
                            "io.micronaut.tracing.brave.http.BraveTracingServerFilter",
                            "io.micronaut.tracing.opentracing.instrument.http.OpenTracingClientFilter",
                            "io.micronaut.tracing.opentracing.instrument.http.OpenTracingServerFilter"
                    ),
                    List.of("io.micronaut.http.HttpRequest", "io.micronaut.http.client.HttpClient"),
                    "Micronaut Tracing HTTP client/server filters were detected.",
                    "HTTP classes are present, but no Micronaut Tracing HTTP integration was detected.",
                    "Micronaut Tracing HTTP integration was not detected."
            ),
            new InstrumentationCheck(
                    "gRPC",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.instrument.grpc.GrpcClientTracingInterceptorFactory",
                            "io.micronaut.tracing.opentelemetry.instrument.grpc.GrpcServerTracingInterceptorFactory"
                    ),
                    List.of("io.grpc.ManagedChannel"),
                    "Micronaut Tracing gRPC interceptors were detected.",
                    "gRPC classes are present, but no Micronaut Tracing gRPC integration was detected.",
                    "Micronaut Tracing gRPC integration was not detected."
            ),
            new InstrumentationCheck(
                    "JDBC",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.instrument.jdbc.DataSourceBeanCreatedEventListener",
                            "io.micronaut.tracing.opentelemetry.instrument.jdbc.JdbcTelemetryConfiguration"
                    ),
                    List.of("java.sql.Driver"),
                    "Micronaut Tracing JDBC datasource instrumentation was detected.",
                    "JDBC classes are present, but no Micronaut Tracing JDBC integration was detected.",
                    "Micronaut Tracing JDBC integration was not detected."
            ),
            new InstrumentationCheck(
                    "R2DBC",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.instrument.r2dbc.R2dbcConnectionFactoryFactory",
                            "io.micronaut.tracing.opentelemetry.instrument.r2dbc.R2dbcTelemetryConfiguration"
                    ),
                    List.of("io.r2dbc.spi.ConnectionFactory"),
                    "Micronaut Tracing R2DBC connection factory instrumentation was detected.",
                    "R2DBC classes are present, but no Micronaut Tracing R2DBC integration was detected.",
                    "Micronaut Tracing R2DBC integration was not detected."
            ),
            new InstrumentationCheck(
                    "Kafka",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaTelemetryConsumerTracingInstrumentation",
                            "io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaTelemetryProducerTracingInstrumentation",
                            "io.micronaut.tracing.opentelemetry.instrument.kafka.TracingConsumerInterceptor",
                            "io.micronaut.tracing.opentelemetry.instrument.kafka.TracingProducerInterceptor"
                    ),
                    List.of("org.apache.kafka.clients.consumer.Consumer", "org.apache.kafka.clients.producer.Producer"),
                    "Micronaut Tracing Kafka producer/consumer instrumentation was detected.",
                    "Kafka client classes are present, but no Micronaut Tracing Kafka integration was detected.",
                    "Micronaut Tracing Kafka integration was not detected."
            ),
            new InstrumentationCheck(
                    "AWS SDK",
                    List.of("io.micronaut.tracing.opentelemetry.xray.SdkClientBuilderListener"),
                    List.of("software.amazon.awssdk.core.SdkClient"),
                    "Micronaut Tracing AWS SDK listener was detected.",
                    "AWS SDK classes are present, but no Micronaut Tracing AWS SDK integration was detected.",
                    "Micronaut Tracing AWS SDK integration was not detected."
            ),
            new InstrumentationCheck(
                    "Logback appender",
                    List.of(
                            "io.micronaut.tracing.opentelemetry.log.OpenTelemetryLogbackAppenderInstaller",
                            "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"
                    ),
                    List.of("ch.qos.logback.classic.Logger"),
                    "OpenTelemetry Logback appender integration was detected.",
                    "Logback classes are present, but no OpenTelemetry Logback appender integration was detected.",
                    "OpenTelemetry Logback appender integration was not detected."
            ),
            new InstrumentationCheck(
                    "Logback MDC",
                    List.of("io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender"),
                    List.of("org.slf4j.MDC"),
                    "OpenTelemetry Logback MDC integration was detected.",
                    "SLF4J MDC is present, but no OpenTelemetry Logback MDC integration was detected.",
                    "OpenTelemetry Logback MDC integration was not detected."
            )
    );

    private final Environment environment;
    private final ApplicationConfiguration applicationConfiguration;
    private final BeanContext beanContext;
    private final TracingRedactor redactor;
    private final ClassPresence classPresence;

    /**
     * Creates a tracing diagnostics service.
     *
     * @param environment Micronaut environment
     * @param applicationConfiguration application configuration
     * @param beanContext bean context
     * @param redactor tracing redactor
     */
    public TracingDiagnosticsService(Environment environment,
                                     ApplicationConfiguration applicationConfiguration,
                                     BeanContext beanContext,
                                     TracingRedactor redactor) {
        this(environment, applicationConfiguration, beanContext, redactor, ClassPresence.contextClassLoader());
    }

    TracingDiagnosticsService(Environment environment,
                              ApplicationConfiguration applicationConfiguration,
                              BeanContext beanContext,
                              TracingRedactor redactor,
                              ClassPresence classPresence) {
        this.environment = environment;
        this.applicationConfiguration = applicationConfiguration;
        this.beanContext = beanContext;
        this.redactor = redactor;
        this.classPresence = classPresence;
    }

    TracingBody getBody() {
        List<TracingProviderInfo> providers = providers();
        List<TracingExporterInfo> exporters = exporters();
        List<TracingKeyValue> resourceAttributes = resourceAttributes();
        List<TracingInstrumentationInfo> instrumentations = instrumentations();
        List<TracingKeyValue> configuration = configuration();
        List<TracingDiagnostic> diagnostics = diagnostics(providers, exporters);
        String serviceName = serviceName();
        String sampler = property("otel.traces.sampler").orElse(NOT_CONFIGURED);
        String propagators = property("otel.propagators").orElse(NOT_CONFIGURED);
        int presentInstrumentationCount = (int) instrumentations.stream().filter(i -> PRESENT.equals(i.status())).count();
        return new TracingBody(
                providers,
                exporters,
                resourceAttributes,
                instrumentations,
                configuration,
                diagnostics,
                serviceName,
                sampler,
                propagators,
                providers.isEmpty() ? "not detected" : providers.size() + " detected",
                presentInstrumentationCount
        );
    }

    private List<TracingProviderInfo> providers() {
        List<TracingProviderInfo> providers = new ArrayList<>();
        for (ClassCheck provider : PROVIDERS) {
            if (classPresence.isPresent(provider.className()) || containsBean(provider.className())) {
                providers.add(new TracingProviderInfo(provider.name(), "present", provider.detail()));
            }
        }
        return providers;
    }

    private List<TracingExporterInfo> exporters() {
        return List.of(
                exporter("Traces", "otel.traces.exporter", "otel.exporter.otlp.traces.endpoint"),
                exporter("Metrics", "otel.metrics.exporter", "otel.exporter.otlp.metrics.endpoint"),
                exporter("Logs", "otel.logs.exporter", "otel.exporter.otlp.logs.endpoint")
        );
    }

    private TracingExporterInfo exporter(String signal, String exporterKey, String endpointKey) {
        Optional<String> configuredExporter = property(exporterKey);
        String exporter = configuredExporter.orElse(NONE);
        String endpoint = property(endpointKey)
                .or(() -> property("otel.exporter.otlp.endpoint"))
                .map(value -> redactor.redact(endpointKey, value))
                .orElse(NOT_CONFIGURED);
        String status = configuredExporter
                .map(value -> NONE.equalsIgnoreCase(value) ? "disabled" : "configured")
                .orElse("default disabled");
        return new TracingExporterInfo(signal, exporter, endpoint, status, configuredExporter.isPresent());
    }

    private List<TracingInstrumentationInfo> instrumentations() {
        return INSTRUMENTATIONS.stream()
                .map(this::instrumentation)
                .toList();
    }

    private TracingInstrumentationInfo instrumentation(InstrumentationCheck check) {
        if (anyClassOrBeanPresent(check.tracingClasses())) {
            return new TracingInstrumentationInfo(check.name(), PRESENT, check.presentDetail());
        }
        if (anyClassPresent(check.baseClasses())) {
            return new TracingInstrumentationInfo(check.name(), "unknown", check.unknownDetail());
        }
        return new TracingInstrumentationInfo(check.name(), "absent", check.absentDetail());
    }

    private List<TracingKeyValue> configuration() {
        List<TracingKeyValue> values = new ArrayList<>();
        for (String key : CONFIGURATION_KEYS) {
            property(key).ifPresent(value -> values.add(keyValue(key, configurationValue(key, value), "configuration")));
        }
        return values;
    }

    private List<TracingKeyValue> resourceAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        property(RESOURCE_ATTRIBUTES_KEY).ifPresent(value -> {
            for (String entry : value.split(",")) {
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) {
                    int equals = trimmed.indexOf('=');
                    if (equals > 0) {
                        attributes.put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
                    } else {
                        attributes.put(trimmed, "");
                    }
                }
            }
        });
        if (attributes.isEmpty() && !NOT_CONFIGURED.equals(serviceName())) {
            attributes.put("service.name", serviceName());
        }
        return attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> keyValue(entry.getKey(), entry.getValue(), "resource"))
                .toList();
    }

    private List<TracingDiagnostic> diagnostics(List<TracingProviderInfo> providers, List<TracingExporterInfo> exporters) {
        List<TracingDiagnostic> diagnostics = new ArrayList<>();
        if (providers.isEmpty()) {
            diagnostics.add(new TracingDiagnostic("info", "No tracing provider was detected on the application classpath."));
        }
        long providerFamilies = providers.stream()
                .map(TracingProviderInfo::name)
                .map(this::providerFamily)
                .distinct()
                .count();
        if (providerFamilies > 1) {
            diagnostics.add(new TracingDiagnostic(WARNING, "Multiple tracing provider families were detected. Check for duplicate agent and application instrumentation."));
        }
        exporters.stream()
                .filter(exporter -> exporter.configured() && NONE.equalsIgnoreCase(exporter.exporter()))
                .forEach(exporter -> diagnostics.add(new TracingDiagnostic("info", exporter.signal() + " exporter is set to none.")));
        configuredExporterWithoutKnownDependency(exporters).ifPresent(diagnostics::add);
        kafkaTopicConflict().ifPresent(diagnostics::add);
        property("otel.sdk.disabled")
                .filter("true"::equalsIgnoreCase)
                .ifPresent(value -> diagnostics.add(new TracingDiagnostic(WARNING, "OpenTelemetry SDK is disabled by configuration.")));
        diagnostics.sort(Comparator.comparing(TracingDiagnostic::severity).reversed().thenComparing(TracingDiagnostic::message));
        return diagnostics;
    }

    private Optional<TracingDiagnostic> configuredExporterWithoutKnownDependency(List<TracingExporterInfo> exporters) {
        boolean hasConfiguredExporter = exporters.stream().anyMatch(exporter -> exporter.configured() && !"none".equalsIgnoreCase(exporter.exporter()));
        if (hasConfiguredExporter
                && !classPresence.isPresent("io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter")
                && !classPresence.isPresent("io.opentelemetry.exporter.zipkin.ZipkinSpanExporter")) {
            return Optional.of(new TracingDiagnostic(WARNING, "An exporter is configured, but a known OpenTelemetry exporter implementation was not detected."));
        }
        return Optional.empty();
    }

    private Optional<TracingDiagnostic> kafkaTopicConflict() {
        Optional<String> included = property("kafka.tracing.included-topics");
        Optional<String> excluded = property("kafka.tracing.excluded-topics");
        if (included.isPresent() && excluded.isPresent()) {
            return Optional.of(new TracingDiagnostic(WARNING, "Kafka tracing has both include and exclude topic filters configured."));
        }
        return Optional.empty();
    }

    private TracingKeyValue keyValue(String key, String value, String source) {
        String redactedValue = redactor.redact(key, value);
        return new TracingKeyValue(key, redactedValue, source, TracingRedactor.REDACTED.equals(redactedValue));
    }

    private String configurationValue(String key, String value) {
        if (!RESOURCE_ATTRIBUTES_KEY.equals(key)) {
            return value;
        }
        List<String> redactedAttributes = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
                String attributeKey = trimmed.substring(0, equals).trim();
                String attributeValue = trimmed.substring(equals + 1).trim();
                redactedAttributes.add(attributeKey + "=" + redactor.redact(attributeKey, attributeValue));
            } else {
                redactedAttributes.add(redactor.redact(trimmed, ""));
            }
        }
        return String.join(",", redactedAttributes);
    }

    private String serviceName() {
        return property("otel.service.name")
                .or(applicationConfiguration::getName)
                .orElse(NOT_CONFIGURED);
    }

    private Optional<String> property(String key) {
        return environment.getProperty(key, String.class).filter(value -> !value.isBlank());
    }

    private boolean containsBean(String className) {
        try {
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return beanContext.containsBean(type);
        } catch (ClassNotFoundException | LinkageError _) {
            return false;
        }
    }

    private String providerFamily(String provider) {
        String normalized = provider.toLowerCase(Locale.ROOT);
        if (normalized.contains("opentelemetry")) {
            return "opentelemetry";
        }
        if (normalized.contains("opentracing")) {
            return "opentracing";
        }
        if (normalized.contains("brave") || normalized.contains("zipkin")) {
            return "brave";
        }
        if (normalized.contains("jaeger")) {
            return "jaeger";
        }
        return normalized;
    }

    private boolean anyClassOrBeanPresent(List<String> classNames) {
        return classNames.stream().anyMatch(className -> classPresence.isPresent(className) || containsBean(className));
    }

    private boolean anyClassPresent(List<String> classNames) {
        return classNames.stream().anyMatch(classPresence::isPresent);
    }

    private record ClassCheck(String name, String className, String detail) {
    }

    private record InstrumentationCheck(String name,
                                        List<String> tracingClasses,
                                        List<String> baseClasses,
                                        String presentDetail,
                                        String unknownDetail,
                                        String absentDetail) {
    }
}
