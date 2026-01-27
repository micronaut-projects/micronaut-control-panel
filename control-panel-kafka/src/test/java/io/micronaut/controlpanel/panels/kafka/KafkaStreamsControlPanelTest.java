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

package io.micronaut.controlpanel.panels.kafka;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaStreamsControlPanelTest {

    private static final String NAME = "kafka-streams";

    @Test
    void testComputeSubTopologiesNoSubs() {
        String desc = "No topology";
        var result = KafkaStreamsControlPanel.computeSubTopologies(desc);
        assertEquals(0, result);
    }

    @Test
    void testComputeSubTopologiesOneSub() {
        String desc = """
                Topologies:
                Sub-topology: 0
                Source: source (topics: [input])
                """;
        Object result = KafkaStreamsControlPanel.computeSubTopologies(desc);
        assertEquals(1, result);
    }

    @Test
    void testComputeSubTopologiesTwoSubs() {
        String desc = """
                Topologies:
                Sub-topology: 0
                ...
                Sub-topology: 1
                ...
                """;
        Object result = KafkaStreamsControlPanel.computeSubTopologies(desc);
        assertEquals(2, result);
    }

    @Test
    void testGenerateMermaidFromDescriptionEmpty() {
        String result = KafkaStreamsControlPanel.generateMermaidFromDescription(null);
        assertTrue(result.contains("EMPTY[No topology detected]"));

        result = KafkaStreamsControlPanel.generateMermaidFromDescription("");
        assertTrue(result.contains("EMPTY[No topology detected]"));
    }

    @Test
    void testGenerateMermaidSimpleTopology() {
        String desc = """
                Topologies:
                Sub-topology: 0
                Source: KSTREAM-SOURCE-0 (topics: [input-topic])
                --> KSTREAM-SINK-0
                Sink: KSTREAM-SINK-0 (topic: output-topic)
                """;
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        assertTrue(mermaid.contains("flowchart LR"));
        assertTrue(mermaid.contains("input_topic[input-topic] --> KSTREAM_SOURCE_0((KSTREAM"));
        assertTrue(mermaid.contains("KSTREAM_SOURCE_0((KSTREAM") );
        assertTrue(mermaid.contains("KSTREAM_SINK_0((KSTREAM-SINK-0)) --> output_topic[output"));
        assertTrue(mermaid.contains("subgraph sub_0 [\"Sub-topology: 0\"]"));
    }

    @Test
    void testGenerateMermaidWithStores() {
        String desc = """
                Topologies:
                Sub-topology: 0
                Source: KSTREAM-SOURCE-0 (topics: [input])
                --> KSTREAM-TRANSFORM-0
                Processor: KSTREAM-TRANSFORM-0 (stores: [state-store])
                --> KSTREAM-SINK-0
                Sink: KSTREAM-SINK-0 (topic: output)
                """;
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        assertTrue(mermaid.contains("KSTREAM_TRANSFORM_0(("));
        assertTrue(mermaid.contains("state_store[(state-store)]"));
        assertTrue(mermaid.contains("KSTREAM_TRANSFORM_0 --> state_store"));
    }

    @Test
    void testGenerateMermaidJoin() {
        String desc = """
                Topologies:
                Sub-topology: 0
                Source: KSTREAM-SOURCE-0 (topics: [left])
                Source: KSTREAM-SOURCE-1 (topics: [right])
                --> KSTREAM-JOIN-0
                Processor: KSTREAM-JOIN-0 (stores: [join-store])
                --> KSTREAM-SINK-0
                Sink: KSTREAM-SINK-0 (topic: output)
                """;
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        assertTrue(mermaid.contains("join_store[(join-store)] --> KSTREAM_JOIN_0(("));
    }

    @Test
    void testGenerateMermaidArrowToNone() {
        String desc = """
                Topologies:
                Sub-topology: 0
                Source: KSTREAM-SOURCE-0 (topics: [input])
                --> none
                """;
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        // Should not include arrow to none
        assertFalse(mermaid.contains("none"));
    }
}
