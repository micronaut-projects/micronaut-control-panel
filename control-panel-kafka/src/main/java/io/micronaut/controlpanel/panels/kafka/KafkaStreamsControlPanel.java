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

import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import org.apache.kafka.streams.TopologyDescription;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A per-streams control panel that renders the Kafka Streams topology for each configured builder.
 */
@EachBean(ConfiguredStreamBuilder.class)
@Requires(beans = ConfiguredStreamBuilder.class)
public class KafkaStreamsControlPanel extends AbstractEachBeanControlPanel<KafkaStreamsControlPanel.Body> {

    public static final String NAME = "kafka-streams";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("kafka", "Kafka", "si si-apachekafka");

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsControlPanel.class);
    private static final String HTML_BREAK = "&lt;br/&gt;";
    private static final String CONTROL_PANEL_PATH_PLACEHOLDER = "__CONTROL_PANEL_PATH__";

    private final String beanName;
    private final ConfiguredStreamBuilder builder;
    private final KafkaStreamsRuntimeStateResolver runtimeStateResolver;
    private final String mermaid;
    private final int subTopologies;

    public KafkaStreamsControlPanel(@Parameter String beanName,
                                    ConfiguredStreamBuilder builder,
                                    KafkaStreamsRuntimeStateResolver runtimeStateResolver,
                                    @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.builder = builder;
        this.runtimeStateResolver = runtimeStateResolver;
        TopologyDescription description = builder.build(builder.getConfiguration()).describe();
        this.mermaid = generateMermaidFromDescription(description);
        this.subTopologies = computeSubTopologies(description);
    }

    @Override
    protected String getBeanName() {
        return beanName;
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public Body getBody() {
        return new Body(mermaid, subTopologies, runtimeStateResolver.resolve(beanName, builder));
    }

    @Override
    public String getTitle() {
        return "Kafka Streams: " + getBeanName();
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getBadge() {
        return String.valueOf(subTopologies);
    }

    static int computeSubTopologies(@Nullable TopologyDescription description) {
        if (description == null) {
            return 0;
        }
        return description.subtopologies().size();
    }

    private static String sanitizeId(String s) {
        return s.replaceAll("\\w", "_");
    }

    static String generateMermaidFromDescription(@Nullable TopologyDescription description) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generating Mermaid diagram from topology description: {}", description);
        }

        if (description == null || description.subtopologies().isEmpty()) {
            return "flowchart LR\nEMPTY[No topology detected]";
        }

        return buildMermaid(description);
    }

    private static String buildMermaid(TopologyDescription description) {
        Set<String> outside = new LinkedHashSet<>();
        List<String> internal = new ArrayList<>();

        List<TopologyDescription.Subtopology> subtopologies = description.subtopologies().stream()
                .sorted(Comparator.comparingInt(TopologyDescription.Subtopology::id))
                .toList();

        for (TopologyDescription.Subtopology subtopology : subtopologies) {
            internal.add("subgraph sub_" + subtopology.id() + " [\"Sub-topology: " + subtopology.id() + "\"]");
            List<TopologyDescription.Node> nodes = subtopology.nodes().stream()
                    .sorted(Comparator.comparing(TopologyDescription.Node::name))
                    .toList();
            Set<String> internalEdges = new LinkedHashSet<>();
            for (TopologyDescription.Node node : nodes) {
                if (node instanceof TopologyDescription.Source source) {
                    addSourceEdges(source, outside);
                } else if (node instanceof TopologyDescription.Processor processor) {
                    addProcessorEdges(processor, outside);
                } else if (node instanceof TopologyDescription.Sink sink) {
                    addSinkEdges(sink, outside);
                }

                List<TopologyDescription.Node> successors = node.successors().stream()
                        .sorted(Comparator.comparing(TopologyDescription.Node::name))
                        .toList();
                for (TopologyDescription.Node successor : successors) {
                    addInternalEdge(node.name(), successor.name(), internalEdges);
                }
            }

            internal.addAll(internalEdges);
            internal.add("end");
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        outside.forEach(l -> sb.append(l).append("\n"));
        internal.forEach(l -> sb.append(l).append("\n"));
        return sb.toString();
    }

    private static void addSourceEdges(TopologyDescription.Source source, Collection<String> edges) {
        String name = source.name();
        Set<String> topics = new LinkedHashSet<>(source.topicSet());
        if (topics.isEmpty() && source.topicPattern() != null) {
            topics.add(source.topicPattern().pattern());
        }
        for (String topic : topics) {
            String idT = sanitizeId(topic);
            String idN = sanitizeId(name);
            String topicLabel = topic.replace("-", HTML_BREAK);
            String nameLabel = name.replace("-", HTML_BREAK);
            edges.add(idT + "[" + topicLabel + "] --> " + idN + "(" + nameLabel + ")");
            edges.add(topicClick(idT, topic));
        }
    }

    private static void addSinkEdges(TopologyDescription.Sink sink, Collection<String> edges) {
        String name = sink.name();
        String topic = sink.topic();
        if (topic != null && !topic.isBlank()) {
            String idN = sanitizeId(name);
            String idT = sanitizeId(topic);
            String nameLabel = name.replace("-", HTML_BREAK);
            String topicLabel = topic.replace("-", HTML_BREAK);
            edges.add(idN + "(" + nameLabel + ") --> " + idT + "[" + topicLabel + "]");
            edges.add(topicClick(idT, topic));
        }
    }

    private static String topicClick(String id, String topic) {
        return "click " + id + " \"" + CONTROL_PANEL_PATH_PLACEHOLDER + "/" + KafkaClusterControlPanel.NAME
            + "?topic=" + URLEncoder.encode(topic, StandardCharsets.UTF_8) + "\" \"Open topic " + mermaidString(topic) + "\"";
    }

    private static String mermaidString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void addProcessorEdges(TopologyDescription.Processor processor, Collection<String> edges) {
        String name = processor.name();
        Set<String> stores = processor.stores();
        String idN = sanitizeId(name);
        String nameLabel = name.replace("-", HTML_BREAK);
        boolean isJoin = name.toUpperCase().contains("JOIN");
        for (String store : stores) {
            String idS = sanitizeId(store);
            String storeLabel = store.replace("-", HTML_BREAK);
            String storeNode = idS + "[(" + storeLabel + ")]";
            String procNode = idN + "(" + nameLabel + ")";
            if (isJoin) {
                edges.add(storeNode + " --> " + procNode);
            } else {
                edges.add(procNode + " --> " + storeNode);
            }
        }
    }

    private static void addInternalEdge(String from, String to, Collection<String> internal) {
        String fromId = sanitizeId(from);
        String fromLabel = from.replace("-", HTML_BREAK);
        String toId = sanitizeId(to);
        String toLabel = to.replace("-", HTML_BREAK);
        internal.add(fromId + "(" + fromLabel + ") --> " + toId + "(" + toLabel + ")");
    }

    /**
     * Payload for the Kafka Streams panel rendering.
     *
     * @param mermaid       Mermaid diagram for the topology
     * @param subTopologies count of sub-topologies
     * @param runtimeState  Kafka Streams runtime state exposed through Micronaut Health
     */
    @ReflectiveAccess
    public record Body(String mermaid, int subTopologies, KafkaStreamsRuntimeState runtimeState) { }
}
