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

final class KafkaStreamsControlPanelTest {

    @Test
    void testGenerateMermaidFromDescriptionNull() {
        String result = KafkaStreamsControlPanel.generateMermaidFromDescription(null);
        Assertions.assertEquals("flowchart LR\nEMPTY[No topology detected]", result);
    }

    @Test
    void testGenerateMermaidFromDescriptionBlank() {
        String result = KafkaStreamsControlPanel.generateMermaidFromDescription("   \n\n\t  ");
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
    private static String quotedLabel(String s) { return tokenQuoted(labelWithBreaks(s)); }

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

    private static String edgeRegexStoreToNode(String store, String node) {
        return tokenQuoted("[(" + labelWithBreaks(store) + ")]") + ".*" + tokenQuoted("(" + labelWithBreaks(node) + ")");
    }



    // Optional helper to assert absence
    private static void assertNotContainsNormalized(String full, String unexpected) {
        String nf = normalize(full);
        String nu = normalize(unexpected);
        Assertions.assertFalse(nf.contains(nu), () -> "Unexpected snippet was found but should not be.\nUnexpected:\n" + nu + "\nActual:\n" + nf);
    }

    // Build a simple edge snippet using labels (already labelWithBreaks converted)
    private static String edgeSnippet(String fromLabel, String toLabel) {
        return labelWithBreaks(fromLabel) + ") --> " + labelWithBreaks(toLabel) + ")";
    }

    @Test
    void testGenerateMermaidSimpleTopology() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Source: KSTREAM-SOURCE-0 (topics: [input-topic])",
                "--> KSTREAM-SINK-0",
                "Sink: KSTREAM-SINK-0 (topic: output-topic)"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);

        assertMatchesRegexNormalized(mermaid, edgeRegexTopicToNode("input-topic", "KSTREAM-SOURCE-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("KSTREAM-SOURCE-0", "KSTREAM-SINK-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToTopic("KSTREAM-SINK-0", "output-topic"));
        assertContainsNormalized(mermaid, "subgraph sub_0 [\"Sub-topology: 0\"]");
    }

    @Test
    void testGenerateMermaidWithStores() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Source: KSTREAM-SOURCE-0 (topics: [input])",
                "--> KSTREAM-TRANSFORM-0",
                "Processor: KSTREAM-TRANSFORM-0 (stores: [state-store])",
                "--> KSTREAM-SINK-0",
                "Sink: KSTREAM-SINK-0 (topic: output)"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);

        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToStore("KSTREAM-TRANSFORM-0", "state-store"));
    }

    @Test
    void testGenerateMermaidJoin() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Source: KSTREAM-SOURCE-0 (topics: [left])",
                "Source: KSTREAM-SOURCE-1 (topics: [right])",
                "--> KSTREAM-JOIN-0",
                "Processor: KSTREAM-JOIN-0 (stores: [join-store])",
                "--> KSTREAM-SINK-0",
                "Sink: KSTREAM-SINK-0 (topic: output)"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);

        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("join-store", "KSTREAM-JOIN-0"));
    }

    @Test
    void testGenerateMermaidArrowToNone() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Processor: KSTREAM-PROC-0 (stores: [])",
                "--> none"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        assertNotContainsNormalized(mermaid, ") --> none(");
    }

    @Test
    void testComputeSubTopologiesTwoSubs() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Sub-topology: 1"
        );
        int count = KafkaStreamsControlPanel.computeSubTopologies(desc);
        Assertions.assertEquals(2, count);

        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        assertContainsNormalized(mermaid, "subgraph sub_0 [\"Sub-topology: 0\"]");
        assertContainsNormalized(mermaid, "subgraph sub_1 [\"Sub-topology: 1\"]");
    }

    @Test
    void testSinkWithEmptyTopicDoesNotRenderEdge() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Source: KSTREAM-SOURCE-0 (topics: [in])",
                "--> KSTREAM-SINK-0",
                "Sink: KSTREAM-SINK-0 (topic: )"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);
        Assertions.assertFalse(mermaid.contains(") --> ["));
    }

    @Test
    void testHyphenLabelBreaks() {
        String desc = String.join("\n",
                "Topologies:",
                "Sub-topology: 0",
                "Source: KSTREAM-SOURCE-0 (topics: [a-b])",
                "--> KSTREAM-TRANSFORM-1",
                "Processor: KSTREAM-TRANSFORM-1 (stores: [])",
                "--> KSTREAM-SINK-1",
                "Sink: KSTREAM-SINK-1 (topic: out-put)"
        );
        String mermaid = KafkaStreamsControlPanel.generateMermaidFromDescription(desc);

        assertMatchesRegexNormalized(mermaid, edgeRegexTopicToNode("a-b", "KSTREAM-SOURCE-0"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToNode("KSTREAM-SOURCE-0", "KSTREAM-TRANSFORM-1"));
        assertMatchesRegexNormalized(mermaid, edgeRegexNodeToTopic("KSTREAM-SINK-1", "out-put"));
    }
}
