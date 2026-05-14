/*
 * Unit tests for KafkaStreamsControlPanel parsing and Mermaid generation.
 *
 * This class only provides helpers and scaffolding for subsequent tests.
 * Guardrails:
 * - Use structure/contains-based assertions on human-readable labels (Mermaid)
 * - Do NOT assert on internal node IDs produced by sanitizeId
 * - Convert '-' in labels to "&lt;br/&gt;" for expectations to match production output
 */
package io.micronaut.controlpanel.panels.kafka;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyDescription;
import org.apache.kafka.streams.processor.RecordContext;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.TopicNameExtractor;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

final class KafkaStreamsControlPanelTest {

    @Test
    void testGenerateMermaidFromDescriptionNull() {
        String result = KafkaStreamsControlPanel.generateMermaidFromDescription(null);
        Assertions.assertEquals("flowchart LR\nEMPTY[No topology detected]", result);
    }

    @Test
    void testGenerateMermaidFromDescriptionEmptyTopology() {
        Topology topology = new Topology();
        String result = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());
        Assertions.assertEquals("flowchart LR\nEMPTY[No topology detected]", result);
    }


    // Normalize string for robust contains checks: trim, unify newlines, collapse spaces
    private static String normalize(String s) {
        if (s == null) return "";
        String n = s.replace("\r\n", "\n").replace('\r', '\n');
        // collapse multiple spaces
        n = n.replaceAll("[ \t]+", " ");
        // collapse multiple blank lines
        n = n.replaceAll("\n+", "\n");
        return n.trim();
    }

    // Replace '-' with HTML line break used by production code in labels
    private static String labelWithBreaks(String raw) {
        return raw == null ? "" : raw.replace("-", "&lt;br/&gt;");
    }

    // Assert that normalized full contains normalized expected
    private static void assertContainsNormalized(String full, String expected) {
        String nf = normalize(full);
        String ne = normalize(expected);
        Assertions.assertTrue(nf.contains(ne), () -> "Expected snippet not found.\nExpected:\n" + ne + "\nActual:\n" + nf);
    }

    // Assert that normalized full matches a regex pattern (find)
    private static void assertMatchesRegexNormalized(String full, String regex) {
        String nf = normalize(full);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        Assertions.assertTrue(p.matcher(nf).find(), () -> "Expected regex did not match.\nRegex:\n" + regex + "\nActual:\n" + nf);
    }

    private static String tokenQuoted(String token) { return java.util.regex.Pattern.quote(token); }

    private static String edgeRegexTopicToNode(String topic, String node) {
        return tokenQuoted("[" + labelWithBreaks(topic) + "]") + ".*" + tokenQuoted("(" + labelWithBreaks(node) + ")");
    }

    private static String edgeRegexNodeToTopic(String node, String topic) {
        return tokenQuoted("(" + labelWithBreaks(node) + ")") + ".*" + tokenQuoted("[" + labelWithBreaks(topic) + "]");
    }

    private static String edgeRegexNodeToNode(String from, String to) {
        return tokenQuoted("(" + labelWithBreaks(from) + ")") + ".*" + tokenQuoted("(" + labelWithBreaks(to) + ")");
    }

    private static String edgeRegexNodeToStore(String node, String store) {
        return tokenQuoted("(" + labelWithBreaks(node) + ")") + ".*" + tokenQuoted("[(" + labelWithBreaks(store) + ")]");
    }

    // Optional helper to assert absence
    private static void assertNotContainsNormalized(String full, String unexpected) {
        String nf = normalize(full);
        String nu = normalize(unexpected);
        Assertions.assertFalse(nf.contains(nu), () -> "Unexpected snippet was found but should not be.\nUnexpected:\n" + nu + "\nActual:\n" + nf);
    }

    @Test
    void testGenerateMermaidSimpleTopology() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "input-topic");
        topology.addSink("KSTREAM-SINK-0", "output-topic", "KSTREAM-SOURCE-0");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());

        assertMatchesRegexNormalized(mermaid, edgeRegexTopicToNode("input-topic", "KSTREAM-SOURCE-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("KSTREAM-SOURCE-0", "KSTREAM-SINK-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToTopic("KSTREAM-SINK-0", "output-topic"));
        assertContainsNormalized(mermaid, "subgraph sub_0 [\"Sub-topology: 0\"]");
    }

    @Test
    void testGenerateMermaidWithStores() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "input");
        addProcessor(topology, "KSTREAM-TRANSFORM-0", "KSTREAM-SOURCE-0");
        addStateStore(topology, "state-store", "KSTREAM-TRANSFORM-0");
        topology.addSink("KSTREAM-SINK-0", "output", "KSTREAM-TRANSFORM-0");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());

        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToStore("KSTREAM-TRANSFORM-0", "state-store"));
    }

    @Test
    void testGenerateMermaidJoin() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "left");
        topology.addSource("KSTREAM-SOURCE-1", "right");
        addProcessor(topology, "KSTREAM-JOIN-0", "KSTREAM-SOURCE-0", "KSTREAM-SOURCE-1");
        addStateStore(topology, "join-store", "KSTREAM-JOIN-0");
        topology.addSink("KSTREAM-SINK-0", "output", "KSTREAM-JOIN-0");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());

        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("join-store", "KSTREAM-JOIN-0"));
    }

    @Test
    void testGenerateMermaidProcessorWithoutSuccessors() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "input");
        addProcessor(topology, "KSTREAM-PROC-0", "KSTREAM-SOURCE-0");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());
        assertNotContainsNormalized(mermaid, labelWithBreaks("KSTREAM-PROC-0") + ") -->");
    }

    @Test
    void testComputeSubTopologiesTwoSubs() {
        Topology topology = new Topology();
        topology.addSource("SOURCE-0", "in-0");
        topology.addSink("SINK-0", "out-0", "SOURCE-0");
        topology.addSource("SOURCE-1", "in-1");
        topology.addSink("SINK-1", "out-1", "SOURCE-1");
        TopologyDescription description = topology.describe();
        int count = KafkaStreamsControlPanel.computeSubTopologies(description);
        Assertions.assertEquals(2, count);

        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(description);
        assertContainsNormalized(mermaid, "subgraph sub_0 [\"Sub-topology: 0\"]");
        assertContainsNormalized(mermaid, "subgraph sub_1 [\"Sub-topology: 1\"]");
    }

    @Test
    void testSinkWithEmptyTopicDoesNotRenderEdge() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "in");
        topology.addSink("KSTREAM-SINK-0", new TopicNameExtractor<Object, Object>() {
            @Override
            public String extract(Object key, Object value, RecordContext recordContext) {
                return "ignored";
            }
        }, "KSTREAM-SOURCE-0");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());
        Assertions.assertFalse(mermaid.contains(") --> ["));
    }

    @Test
    void testRuntimeStateMapsVisibleKafkaStreamsHealthDetails() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP)
                .details(Map.of(
                        "kafkaStreams", HealthResult.builder("kafkaStreams", HealthStatus.UP)
                                .details(Map.of(
                                        "orders-streams", HealthResult.builder("orders-streams", HealthStatus.UP)
                                                .details(Map.of(
                                                        "orders-thread-1", Map.of(
                                                                "threadName", "orders-thread-1",
                                                                "threadState", "RUNNING",
                                                                "adminClientId", "orders-admin",
                                                                "consumerClientId", "orders-consumer",
                                                                "restoreConsumerClientId", "orders-restore",
                                                                "producerClientIds", List.of("orders-producer-1", "orders-producer-2"),
                                                                "activeTasks", Map.of(
                                                                        "taskId", new TaskId(0, 1),
                                                                        "partitions", List.of("orders-0", "orders-1")
                                                                ),
                                                                "standbyTasks", Map.of(
                                                                        "taskId", new TaskId(1, 0),
                                                                        "partitions", List.of("orders-standby-0")
                                                                )
                                                        )
                                                ))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        KafkaStreamsRuntimeState state = HealthKafkaStreamsRuntimeStateResolver.resolve("default", builder, health);

        Assertions.assertTrue(state.available());
        Assertions.assertEquals("UP", state.status());
        Assertions.assertEquals(1, state.threads().size());
        KafkaStreamsRuntimeState.ThreadState thread = state.threads().getFirst();
        Assertions.assertEquals("orders-thread-1", thread.name());
        Assertions.assertEquals("RUNNING", thread.state());
        Assertions.assertEquals("orders-admin", thread.adminClientId());
        Assertions.assertEquals("orders-consumer", thread.consumerClientId());
        Assertions.assertEquals("orders-restore", thread.restoreConsumerClientId());
        Assertions.assertEquals(List.of("orders-producer-1", "orders-producer-2"), thread.producerClientIds());
        Assertions.assertEquals("0_1", thread.activeTasks().taskId());
        Assertions.assertEquals(2, thread.activeTasks().partitionCount());
        Assertions.assertEquals("1_0", thread.standbyTasks().taskId());
        Assertions.assertEquals(1, thread.standbyTasks().partitionCount());
    }

    @Test
    void testRuntimeStateReadsVisibleHealthEndpointDetails() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP)
                .details(Map.of(
                        "kafkaStreams", HealthResult.builder("kafkaStreams", HealthStatus.UP)
                                .details(Map.of(
                                        "orders-streams", HealthResult.builder("orders-streams", HealthStatus.UP)
                                                .details(Map.of(
                                                        "orders-thread-1", Map.of(
                                                                "threadName", "orders-thread-1",
                                                                "threadState", "RUNNING"
                                                        )
                                                ))
                                                .build()
                                ))
                                .build()
                ))
                .build();
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(null)).thenReturn(Mono.just(health));

        KafkaStreamsRuntimeState state = new HealthKafkaStreamsRuntimeStateResolver(endpoint).resolve("default", builder);

        Assertions.assertTrue(state.available());
        Assertions.assertEquals("UP", state.status());
        Assertions.assertEquals("orders-thread-1", state.threads().getFirst().name());
    }

    @Test
    void testRuntimeStateUsesCurrentRequestPrincipalForHealthDetails() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP)
                .details(Map.of(
                        "kafkaStreams", HealthResult.builder("kafkaStreams", HealthStatus.UP)
                                .details(Map.of("orders-streams", HealthResult.builder("orders-streams", HealthStatus.UP).build()))
                                .build()
                ))
                .build();
        Principal principal = () -> "sherlock";
        HealthEndpoint endpoint = Mockito.mock(HealthEndpoint.class);
        Mockito.when(endpoint.getHealth(principal)).thenReturn(Mono.just(health));
        HttpRequest<?> request = HttpRequest.GET("/control-panel/kafka-streams/default");
        request.setUserPrincipal(principal);

        KafkaStreamsRuntimeState state = ServerRequestContext.with(
                request,
                (java.util.function.Supplier<KafkaStreamsRuntimeState>) () ->
                        new HealthKafkaStreamsRuntimeStateResolver(endpoint).resolve("default", builder)
        );

        Assertions.assertTrue(state.available());
        Mockito.verify(endpoint).getHealth(principal);
    }

    @Test
    void testRuntimeStateMatchesClientIdAndNormalizedHealthKeys() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("kafkaStreams()", HealthStatus.DOWN)
                .details(Map.of(
                        "orders-client()", HealthResult.builder("orders-client", HealthStatus.DOWN)
                                .details(Map.of("error", "thread stopped"))
                                .build()
                ))
                .build();

        KafkaStreamsRuntimeState state = HealthKafkaStreamsRuntimeStateResolver.resolve("default", builder, health);

        Assertions.assertTrue(state.available());
        Assertions.assertEquals("DOWN", state.status());
        Assertions.assertEquals("thread stopped", state.message());
        Assertions.assertFalse(state.hasThreads());
    }

    @Test
    void testRuntimeStateHandlesPartialThreadDetailsDefensively() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP)
                .details(Map.of(
                        "kafkaStreams", HealthResult.builder("kafkaStreams", HealthStatus.UP)
                                .details(Map.of(
                                        "default", HealthResult.builder("default", HealthStatus.UP)
                                                .details(Map.of(
                                                        "orders-thread-1", Map.of(
                                                                "threadName", "orders-thread-1",
                                                                "threadState", "REBALANCING",
                                                                "producerClientIds", "orders-producer",
                                                                "activeTasks", "unexpected",
                                                                "standbyTasks", Map.of("partitions", "orders-0")
                                                        )
                                                ))
                                                .build()
                                ))
                                .build()
                ))
                .build();

        KafkaStreamsRuntimeState state = HealthKafkaStreamsRuntimeStateResolver.resolve("default", builder, health);

        KafkaStreamsRuntimeState.ThreadState thread = state.threads().getFirst();
        Assertions.assertEquals(List.of("orders-producer"), thread.producerClientIds());
        Assertions.assertFalse(thread.activeTasks().available());
        Assertions.assertTrue(thread.standbyTasks().available());
        Assertions.assertEquals(1, thread.standbyTasks().partitionCount());
    }

    @Test
    void testRuntimeStateUnavailableWhenHealthDetailsAreAbsent() {
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        HealthResult health = HealthResult.builder("composite", HealthStatus.UP).build();

        KafkaStreamsRuntimeState state = HealthKafkaStreamsRuntimeStateResolver.resolve("default", builder, health);

        Assertions.assertFalse(state.available());
        Assertions.assertEquals("UNKNOWN", state.status());
        Assertions.assertTrue(state.threads().isEmpty());
        Assertions.assertEquals(HealthKafkaStreamsRuntimeStateResolver.UNAVAILABLE_MESSAGE, state.message());
    }

    @Test
    void testUnavailableRuntimeStateResolverReturnsUnavailableState() {
        KafkaStreamsRuntimeState state = new UnavailableKafkaStreamsRuntimeStateResolver()
                .resolve("default", configuredStreamBuilder("orders-streams", "orders-client"));

        Assertions.assertFalse(state.available());
        Assertions.assertEquals("UNKNOWN", state.status());
        Assertions.assertEquals("Runtime state is unavailable because Micronaut Health details are not visible.", state.message());
    }

    @Test
    void testControlPanelResolvesRuntimeStateForEachBodyCall() {
        AtomicInteger calls = new AtomicInteger();
        ConfiguredStreamBuilder builder = configuredStreamBuilder("orders-streams", "orders-client");
        KafkaStreamsRuntimeStateResolver resolver = (beanName, streamBuilder) ->
                KafkaStreamsRuntimeState.available("UP-" + calls.incrementAndGet(), null, List.of());
        KafkaStreamsControlPanel panel = new KafkaStreamsControlPanel(
                "default",
                builder,
                resolver,
                new ControlPanelConfiguration(KafkaStreamsControlPanel.NAME)
        );

        KafkaStreamsControlPanel.Body first = panel.getBody();
        KafkaStreamsControlPanel.Body second = panel.getBody();

        Assertions.assertEquals("Kafka Streams: default", panel.getTitle());
        Assertions.assertEquals("0", panel.getBadge());
        Assertions.assertEquals("UP-1", first.runtimeState().status());
        Assertions.assertEquals("UP-2", second.runtimeState().status());
        Assertions.assertEquals("flowchart LR\nEMPTY[No topology detected]", first.mermaid());
    }

    @Test
    void testRuntimeStateModelNormalizesNullCollectionsAndBlankStatus() {
        KafkaStreamsRuntimeState.ThreadState thread = new KafkaStreamsRuntimeState.ThreadState(
                "orders-thread-1",
                "RUNNING",
                null,
                null,
                null,
                null,
                null,
                null
        );

        KafkaStreamsRuntimeState state = KafkaStreamsRuntimeState.available(" ", null, List.of(thread));

        Assertions.assertEquals("UNKNOWN", state.status());
        Assertions.assertTrue(state.hasThreads());
        Assertions.assertFalse(thread.hasProducerClientIds());
        Assertions.assertFalse(thread.activeTasks().available());
        Assertions.assertFalse(thread.standbyTasks().available());
    }

    @Test
    void testRuntimeStateModelExposesTemplateStateClasses() {
        KafkaStreamsRuntimeState up = KafkaStreamsRuntimeState.available("UP", null, List.of());
        KafkaStreamsRuntimeState down = KafkaStreamsRuntimeState.available("DOWN", null, List.of());
        KafkaStreamsRuntimeState outOfService = KafkaStreamsRuntimeState.available("OUT_OF_SERVICE", null, List.of());
        KafkaStreamsRuntimeState unknown = KafkaStreamsRuntimeState.available("UNKNOWN", null, List.of());
        KafkaStreamsRuntimeState unavailable = KafkaStreamsRuntimeState.unavailable("Health details hidden");

        Assertions.assertEquals("cp-kafka-badge-success", up.statusBadgeClass());
        Assertions.assertEquals("cp-kafka-runtime--available", up.sectionStateClass());
        Assertions.assertFalse(up.errorState());
        Assertions.assertEquals("badge-destructive", down.statusBadgeClass());
        Assertions.assertEquals("cp-kafka-runtime--error", down.sectionStateClass());
        Assertions.assertTrue(down.errorState());
        Assertions.assertEquals("badge-destructive", outOfService.statusBadgeClass());
        Assertions.assertEquals("cp-kafka-runtime--error", outOfService.sectionStateClass());
        Assertions.assertTrue(outOfService.errorState());
        Assertions.assertEquals("badge-secondary", unknown.statusBadgeClass());
        Assertions.assertEquals("cp-kafka-runtime--available", unknown.sectionStateClass());
        Assertions.assertFalse(unknown.errorState());
        Assertions.assertEquals("badge-secondary", unavailable.statusBadgeClass());
        Assertions.assertEquals("cp-kafka-runtime--unavailable", unavailable.sectionStateClass());
        Assertions.assertFalse(unavailable.errorState());

        Assertions.assertEquals("badge-secondary", threadState(null).stateBadgeClass());
        Assertions.assertEquals("cp-kafka-badge-success", threadState("RUNNING").stateBadgeClass());
        Assertions.assertEquals("badge-destructive", threadState("DEAD").stateBadgeClass());
        Assertions.assertEquals("cp-kafka-badge-warning", threadState("REBALANCING").stateBadgeClass());
        Assertions.assertEquals("badge-secondary", threadState("UNKNOWN").stateBadgeClass());
    }

    @Test
    void testKafkaStreamsDetailTemplateUsesResponsiveRuntimeRows() throws IOException {
        String template = resourceText("/views/kafka-streams/detail.hbs");

        Assertions.assertTrue(template.contains("Source: Micronaut <code>HealthEndpoint</code> visible details"));
        Assertions.assertTrue(template.contains("class=\"table cp-data-table-table cp-kafka-threads-table\""));
        Assertions.assertTrue(template.contains("<tr tabindex=\"0\">"));
        Assertions.assertTrue(template.contains("data-label=\"Thread\""));
        Assertions.assertTrue(template.contains("data-label=\"Producer clients\""));
        Assertions.assertTrue(template.contains("<span class=\"badge badge-secondary\">Unavailable</span>"));
        Assertions.assertTrue(template.contains("Runtime state unavailable."));
        Assertions.assertTrue(template.contains("Rendering topology..."));
        Assertions.assertFalse(template.contains("<ul>\n        {{#each body.runtimeState.threads"));
    }

    @Test
    void testHyphenLabelBreaks() {
        Topology topology = new Topology();
        topology.addSource("KSTREAM-SOURCE-0", "a-b");
        addProcessor(topology, "KSTREAM-TRANSFORM-1", "KSTREAM-SOURCE-0");
        topology.addSink("KSTREAM-SINK-1", "out-put", "KSTREAM-TRANSFORM-1");
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(topology.describe());

        assertMatchesRegexNormalized(mermaid, edgeRegexTopicToNode("a-b", "KSTREAM-SOURCE-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("KSTREAM-SOURCE-0", "KSTREAM-TRANSFORM-1"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToTopic("KSTREAM-SINK-1", "out-put"));
    }

    private static void addProcessor(Topology topology, String name, String... parents) {
        ProcessorSupplier<Object, Object, Object, Object> supplier = () -> new Processor<Object, Object, Object, Object>() {
            @Override
            public void init(ProcessorContext<Object, Object> context) {
            }

            @Override
            public void process(Record<Object, Object> record) {
            }

            @Override
            public void close() {
            }
        };
        topology.addProcessor(name, supplier, parents);
    }

    private static void addStateStore(Topology topology, String storeName, String... processors) {
        StoreBuilder<KeyValueStore<String, String>> storeBuilder = Stores.keyValueStoreBuilder(
                Stores.inMemoryKeyValueStore(storeName),
                Serdes.String(),
                Serdes.String()
        );
        topology.addStateStore(storeBuilder, processors);
    }

    private static ConfiguredStreamBuilder configuredStreamBuilder(String applicationId, String clientId) {
        Properties properties = new Properties();
        properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        properties.setProperty(StreamsConfig.CLIENT_ID_CONFIG, clientId);
        properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        return new ConfiguredStreamBuilder(properties);
    }

    private static String resourceText(String path) throws IOException {
        try (var in = KafkaStreamsControlPanelTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(in, () -> "Missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static KafkaStreamsRuntimeState.ThreadState threadState(String state) {
        return new KafkaStreamsRuntimeState.ThreadState(
                "orders-thread-1",
                state,
                null,
                null,
                null,
                List.of(),
                null,
                null
        );
    }
}
