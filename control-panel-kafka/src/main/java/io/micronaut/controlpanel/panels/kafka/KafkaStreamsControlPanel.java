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
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A per-streams control panel that renders the Kafka Streams topology for each configured builder.
 */
@EachBean(ConfiguredStreamBuilder.class)
public class KafkaStreamsControlPanel extends AbstractEachBeanControlPanel<KafkaStreamsControlPanel.Body> {

    public static final String NAME = "kafka-streams";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("kafka", "Kafka", "si si-apachekafka");

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsControlPanel.class);
    private static final String HTML_BREAK = "&lt;br/&gt;";

    private final String beanName;
    private final Body body;

    public KafkaStreamsControlPanel(@Parameter String beanName,
                                    ConfiguredStreamBuilder builder,
                                    @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        String desc = builder.build(builder.getConfiguration()).describe().toString();
        String mermaid = generateMermaidFromDescription(desc);
        int subTopologies = computeSubTopologies(desc);
        this.body = new Body(mermaid, subTopologies);
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
        return body;
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
        return String.valueOf(body.subTopologies());
    }

    static int computeSubTopologies(String desc) {
        Pattern subPattern = Pattern.compile("Sub-topology:\\s*(\\d+)");
        return (int) Arrays.stream(desc.split("\\n"))
                .map(String::trim)
                .filter(line -> subPattern.matcher(line).matches())
                .count();
    }

    private static String sanitizeId(String s) {
        return s.replaceAll("\\w", "_");
    }

    static String generateMermaidFromDescription(String desc) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generating Mermaid diagram from topology description:\n{}", desc);
        }

        if (desc == null || desc.isBlank()) {
            return "flowchart LR\nEMPTY[No topology detected]";
        }

        List<TopologyEvent> events = parseTopology(desc);
        return buildMermaid(events);
    }

    private static List<TopologyEvent> parseTopology(String desc) {
        List<TopologyEvent> events = new ArrayList<>();
        String currentNode = null;

        Pattern subPattern = Pattern.compile("Sub-topology:\\s*(\\d+)");
        Pattern sourcePattern = Pattern.compile("Source:\\s*([^(]+?)\\s*\\(topics:\\s*\\[([^\\]]+)\\]\\)");
        Pattern processorPattern = Pattern.compile("Processor:\\s*([^(]+?)\\s*\\(stores:\\s*\\[([^\\]]+)\\]\\)");
        Pattern sinkPattern = Pattern.compile("Sink:\\s*([^(]+?)\\s*\\(topic:\\s*([^\\)]+)\\)");
        Pattern arrowPattern = Pattern.compile("\\s*-->\\s*(.+)");

        for (String raw : desc.split("\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher m = subPattern.matcher(line);
            if (m.matches()) {
                events.add(new SubTopology(Integer.parseInt(m.group(1))));
                currentNode = null;
                continue;
            }

            m = sourcePattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                List<String> topics = parseList(m.group(2));
                events.add(new SourceEvent(name, topics));
                currentNode = name;
                continue;
            }

            m = processorPattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                List<String> stores = parseList(m.group(2));
                events.add(new ProcessorEvent(name, stores));
                currentNode = name;
                continue;
            }

            m = sinkPattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                String topic = m.group(2).trim();
                events.add(new SinkEvent(name, topic));
                currentNode = name;
                continue;
            }

            m = arrowPattern.matcher(line);
            if (m.matches() && currentNode != null) {
                List<String> targets = parseList(m.group(1));
                events.add(new ArrowEvent(currentNode, targets));
            }
        }

        return events;
    }

    private static String buildMermaid(List<TopologyEvent> events) {
        List<String> outside = new ArrayList<>();
        List<String> internal = new ArrayList<>();
        boolean inSubgraph = false;

        for (TopologyEvent event : events) {
            if (event instanceof SubTopology(int id)) {
                if (inSubgraph) {
                    internal.add("end");
                }
                internal.add("subgraph sub_" + id + " [\"Sub-topology: " + id + "\"]");
                inSubgraph = true;
            } else if (event instanceof SourceEvent se) {
                addSourceEdges(se, outside);
            } else if (event instanceof ProcessorEvent pe) {
                addProcessorEdges(pe, outside);
            } else if (event instanceof SinkEvent se) {
                addSinkEdges(se, outside);
            } else if (event instanceof ArrowEvent(String from, List<String> targets) && inSubgraph) {
                addInternalEdges(from, targets, internal);
            }
        }

        if (inSubgraph) {
            internal.add("end");
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        outside.forEach(l -> sb.append(l).append("\n"));
        internal.forEach(l -> sb.append(l).append("\n"));
        return sb.toString();
    }

    private static List<String> parseList(String str) {
        if (str.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static void addSourceEdges(SourceEvent event, List<String> edges) {
        String name = event.name();
        for (String topic : event.topics()) {
            String idT = sanitizeId(topic);
            String idN = sanitizeId(name);
            String topicLabel = topic.replace("-", HTML_BREAK);
            String nameLabel = name.replace("-", HTML_BREAK);
            edges.add(idT + "[" + topicLabel + "] --> " + idN + "(" + nameLabel + ")");
        }
    }

    private static void addSinkEdges(SinkEvent event, List<String> edges) {
        String name = event.name();
        String topic = event.topic();
        if (!topic.isEmpty()) {
            String idN = sanitizeId(name);
            String idT = sanitizeId(topic);
            String nameLabel = name.replace("-", HTML_BREAK);
            String topicLabel = topic.replace("-", HTML_BREAK);
            edges.add(idN + "(" + nameLabel + ") --> " + idT + "[" + topicLabel + "]");
        }
    }

    private static void addProcessorEdges(ProcessorEvent event, List<String> edges) {
        String name = event.name();
        List<String> stores = event.stores();
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

    private static void addInternalEdges(String from, List<String> targets, List<String> internal) {
        String fromId = sanitizeId(from);
        String fromLabel = from.replace("-", HTML_BREAK);
        for (String target : targets) {
            if ("none".equals(target)) {
                continue;
            }
            String toId = sanitizeId(target);
            String toLabel = target.replace("-", HTML_BREAK);
            internal.add(fromId + "(" + fromLabel + ") --> " + toId + "(" + toLabel + ")");
        }
    }

    sealed interface TopologyEvent permits SubTopology, SourceEvent, ProcessorEvent, SinkEvent, ArrowEvent { }
 
    record SubTopology(int id) implements TopologyEvent { }
 
    record SourceEvent(String name, List<String> topics) implements TopologyEvent { }
 
    record ProcessorEvent(String name, List<String> stores) implements TopologyEvent { }
 
    record SinkEvent(String name, String topic) implements TopologyEvent { }
 
    record ArrowEvent(String from, List<String> targets) implements TopologyEvent { }
 
    @ReflectiveAccess
    public record Body(String mermaid, int subTopologies) { }
}

