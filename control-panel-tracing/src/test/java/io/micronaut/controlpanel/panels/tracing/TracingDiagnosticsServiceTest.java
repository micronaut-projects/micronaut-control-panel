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

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracingDiagnosticsServiceTest {

    @Test
    void reportsOpenTelemetryConfigurationAndRedactsSensitiveValues() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "micronaut.application.name", "orders",
                "otel.service.name", "orders-api",
                "otel.resource.attributes", "deployment.environment=dev,api.key=resource-secret,tenant.id=tenant-a",
                "otel.traces.exporter", "otlp",
                "otel.metrics.exporter", "none",
                "otel.logs.exporter", "none",
                "otel.exporter.otlp.endpoint", "https://user:password@collector.example:4317/v1/traces?token=raw-token&api-key=raw-api-key&clientSecret=raw-client-secret&access%5Ftoken=raw-access-token&region=us",
                "otel.exporter.otlp.headers", "Authorization=Bearer raw-header",
                "otel.propagators", "tracecontext,baggage",
                "otel.traces.sampler", "parentbased_always_on"
        ))) {
            TracingBody body = service(context, "io.opentelemetry.api.OpenTelemetry", "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter").getBody();

            assertEquals("orders-api", body.serviceName());
            assertEquals("parentbased_always_on", body.sampler());
            assertEquals("tracecontext,baggage", body.propagators());
            assertTrue(body.providers().stream().anyMatch(provider -> provider.name().equals("OpenTelemetry")));
            assertTrue(body.exporters().stream().anyMatch(exporter -> exporter.signal().equals("Traces") && exporter.exporter().equals("otlp")));

            String renderedValues = body.configuration().toString() + body.resourceAttributes() + body.exporters();
            assertFalse(renderedValues.contains("resource-secret"));
            assertFalse(renderedValues.contains("raw-header"));
            assertFalse(renderedValues.contains("raw-token"));
            assertFalse(renderedValues.contains("raw-api-key"));
            assertFalse(renderedValues.contains("raw-client-secret"));
            assertFalse(renderedValues.contains("raw-access-token"));
            assertFalse(renderedValues.contains("user:password"));
            assertFalse(renderedValues.contains("tenant-a"));
            assertTrue(renderedValues.contains(TracingRedactor.REDACTED));
        }
    }

    @Test
    void reportsTracingAbsentWithoutFailing() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.application.name", "orders"))) {
            TracingBody body = service(context).getBody();

            assertFalse(body.hasProviders());
            assertEquals("orders", body.serviceName());
            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("No tracing provider")));
        }
    }

    @Test
    void reportsExporterNoneAsDeterministicDiagnostic() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "otel.traces.exporter", "none",
                "otel.metrics.exporter", "none",
                "otel.logs.exporter", "none"
        ))) {
            TracingBody body = service(context, "io.opentelemetry.api.OpenTelemetry").getBody();

            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().equals("Traces exporter is set to none.")));
            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().equals("Metrics exporter is set to none.")));
            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().equals("Logs exporter is set to none.")));
        }
    }

    @Test
    void doesNotReportExporterNoneDiagnosticForDefaultedExporterValues() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            TracingBody body = service(context, "io.opentelemetry.api.OpenTelemetry").getBody();

            assertTrue(body.exporters().stream().allMatch(exporter -> "none".equals(exporter.exporter())));
            assertTrue(body.exporters().stream().allMatch(exporter -> "default disabled".equals(exporter.status())));
            assertFalse(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("exporter is set to none")));
        }
    }

    @Test
    void reportsLegacyProviderAndMultipleProviderCompatibilityWarning() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            TracingBody body = service(context, "io.opentelemetry.api.OpenTelemetry", "brave.Tracing").getBody();

            assertTrue(body.providers().stream().anyMatch(provider -> provider.name().equals("OpenTelemetry")));
            assertTrue(body.providers().stream().anyMatch(provider -> provider.name().equals("Brave/Zipkin")));
            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("Multiple tracing provider families")));
        }
    }

    @Test
    void reportsKafkaIncludeExcludeConflict() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
                "kafka.tracing.included-topics", "orders",
                "kafka.tracing.excluded-topics", "audit"
        ))) {
            TracingBody body = service(context, "io.opentelemetry.api.OpenTelemetry").getBody();

            assertTrue(body.diagnostics().stream().anyMatch(diagnostic -> diagnostic.message().contains("Kafka tracing has both include and exclude")));
        }
    }

    @Test
    void doesNotReportGenericBaseLibrariesAsTracingInstrumentation() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            TracingBody body = service(context,
                    "io.micronaut.http.client.HttpClient",
                    "io.grpc.ManagedChannel",
                    "java.sql.Driver",
                    "io.r2dbc.spi.ConnectionFactory",
                    "org.apache.kafka.clients.producer.Producer",
                    "software.amazon.awssdk.core.SdkClient",
                    "ch.qos.logback.classic.Logger",
                    "org.slf4j.MDC").getBody();

            assertEquals(0, body.presentInstrumentationCount());
            assertEquals("unknown", instrumentation(body, "HTTP client/server").status());
            assertEquals("unknown", instrumentation(body, "gRPC").status());
            assertEquals("unknown", instrumentation(body, "JDBC").status());
            assertEquals("unknown", instrumentation(body, "R2DBC").status());
            assertEquals("unknown", instrumentation(body, "Kafka").status());
            assertEquals("unknown", instrumentation(body, "AWS SDK").status());
            assertEquals("unknown", instrumentation(body, "Logback appender").status());
            assertEquals("unknown", instrumentation(body, "Logback MDC").status());
            assertTrue(body.instrumentations().stream().allMatch(instrumentation -> instrumentation.detail().contains("no ") || instrumentation.detail().contains("but no ")));
        }
    }

    @Test
    void reportsMicronautTracingInstrumentationWhenIntegrationEvidenceIsPresent() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            TracingBody body = service(context,
                    "io.micronaut.tracing.opentelemetry.instrument.http.client.OpenTelemetryClientFilter",
                    "io.micronaut.tracing.opentelemetry.instrument.grpc.GrpcServerTracingInterceptorFactory",
                    "io.micronaut.tracing.opentelemetry.instrument.jdbc.DataSourceBeanCreatedEventListener",
                    "io.micronaut.tracing.opentelemetry.instrument.r2dbc.R2dbcConnectionFactoryFactory",
                    "io.micronaut.tracing.opentelemetry.instrument.kafka.KafkaTelemetryProducerTracingInstrumentation",
                    "io.micronaut.tracing.opentelemetry.xray.SdkClientBuilderListener",
                    "io.micronaut.tracing.opentelemetry.log.OpenTelemetryLogbackAppenderInstaller",
                    "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender").getBody();

            assertEquals(8, body.presentInstrumentationCount());
            assertTrue(body.instrumentations().stream().allMatch(instrumentation -> "present".equals(instrumentation.status())));
        }
    }

    @Test
    void reportsAbsentWhenNeitherBaseLibraryNorTracingIntegrationIsDetected() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            TracingBody body = service(context).getBody();

            assertEquals("absent", instrumentation(body, "Kafka").status());
            assertEquals("absent", instrumentation(body, "R2DBC").status());
            assertEquals("absent", instrumentation(body, "AWS SDK").status());
        }
    }

    private static TracingDiagnosticsService service(ApplicationContext context, String... presentClasses) {
        Set<String> classes = Set.of(presentClasses);
        return new TracingDiagnosticsService(
                context.getEnvironment(),
                context.getBean(io.micronaut.runtime.ApplicationConfiguration.class),
                context,
                new TracingRedactor(),
                classes::contains
        );
    }

    private static TracingInstrumentationInfo instrumentation(TracingBody body, String surface) {
        return body.instrumentations().stream()
                .filter(instrumentation -> instrumentation.surface().equals(surface))
                .findFirst()
                .orElseThrow();
    }
}
