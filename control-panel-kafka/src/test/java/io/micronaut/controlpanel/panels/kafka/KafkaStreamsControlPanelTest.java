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
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthResult;

import java.util.List;
import java.util.Map;
import java.util.Properties;

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
}
