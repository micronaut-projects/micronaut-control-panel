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

    private int computeSubTopologies(String desc) {
        int subTopologies = 0;

        Pattern subPattern = Pattern.compile("Sub-topology:\\s*(\\d+)");

        for (String raw : desc.split("\\n")) {
            String line = raw.trim();
            if (subPattern.matcher(line).matches()) {
                subTopologies++;
            }
        }

        return subTopologies;
    }

    private static String sanitizeId(String s) {
        return s.replaceAll("\\w", "_");
    }

    private static String generateMermaidFromDescription(String desc) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generating Mermaid diagram from topology description: {}", desc);
        }

        if (desc == null || desc.isBlank()) {
            return "flowchart LR\nEMPTY[No topology detected]";
        }

        java.util.List<String> outside = new java.util.ArrayList<>();
        java.util.List<String> subgraphs = new java.util.ArrayList<>();
        String currentNode = null;
        Integer currentSubId = null;
        Pattern subPattern = Pattern.compile("Sub-topology:\\s*(\\d+)");
        Pattern sourcePattern = Pattern.compile("Source:\\s*([^(]+?)\\s*\\(topics:\\s*\\[([^\\]]+)\\]\\)");
        Pattern processorPattern = Pattern.compile("Processor:\\s*([^(]+?)\\s*\\(stores:\\s*\\[([^\\]]+)\\]\\)");
        Pattern sinkPattern = Pattern.compile("Sink:\\s*([^(]+?)\\s*\\(topic:\\s*([^\\)]+)\\)");
        Pattern arrowPattern = Pattern.compile("\\s*-->\\s*(.+)");

        for (String raw : desc.split("\\n")) {
            String line = raw.trim();
            Matcher m = subPattern.matcher(line);
            if (m.matches()) {
                if (currentSubId != null) {
                    subgraphs.add("end");
                }
                currentSubId = Integer.parseInt(m.group(1));
                subgraphs.add("subgraph sub_" + currentSubId + " [\"Sub-topology: " + currentSubId + "\"]");
                continue;
            }

            m = sourcePattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                currentNode = name;
                String topicsStr = m.group(2);
                String[] topics = topicsStr.split(",");
                for (String t : topics) {
                    String topic = t.trim();
                    if (!topic.isEmpty()) {
                        String idT = sanitizeId(topic);
                        String idN = sanitizeId(name);
                        String topicLabel = topic.replace("-", HTML_BREAK);
                        String nameLabel = name.replace("-", HTML_BREAK);
                        outside.add(idT + "[" + topicLabel + "] --> " + idN + "(" + nameLabel + ")");
                    }
                }
                continue;
            }

            m = processorPattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                currentNode = name;
                String storesStr = m.group(2);
                String[] stores = storesStr.split(",");
                for (String s : stores) {
                    String store = s.trim();
                    if (!store.isEmpty()) {
                        String idS = sanitizeId(store);
                        String idN = sanitizeId(name);
                        String storeLabel = store.replace("-", HTML_BREAK);
                        String nameLabel = name.replace("-", HTML_BREAK);
                        String storeNode = idS + "[(" + storeLabel + ")]";
                        String procNode = idN + "(" + nameLabel + ")";
                        boolean isJoin = name.toUpperCase().contains("JOIN");
                        if (isJoin) {
                            outside.add(storeNode + " --> " + procNode);
                        } else {
                            outside.add(procNode + " --> " + storeNode);
                        }
                    }
                }
                continue;
            }

            m = sinkPattern.matcher(line);
            if (m.matches()) {
                String name = m.group(1).trim();
                currentNode = name;
                String topic = m.group(2).trim();
                if (!topic.isEmpty()) {
                    String idN = sanitizeId(name);
                    String idT = sanitizeId(topic);
                    String nameLabel = name.replace("-", HTML_BREAK);
                    String topicLabel = topic.replace("-", HTML_BREAK);
                    outside.add(idN + "(" + nameLabel + ") --> " + idT + "[" + topicLabel + "]");
                }
                continue;
            }

            m = arrowPattern.matcher(line);
            if (m.matches() && currentNode != null) {
                String targetsStr = m.group(1).trim();
                if (!targetsStr.isEmpty()) {
                    String[] targets = targetsStr.split(",");
                    String fromId = sanitizeId(currentNode);
                    String fromLabel = currentNode.replace("-", HTML_BREAK);
                    for (String t : targets) {
                        String target = t.trim();
                        if (!target.isEmpty() && !target.equals("none")) {
                            String toId = sanitizeId(target);
                            String toLabel = target.replace("-", HTML_BREAK);
                            subgraphs.add(fromId + "(" + fromLabel + ") --> " + toId + "(" + toLabel + ")");
                        }
                    }
                }
            }
        }

        if (currentSubId != null) {
            subgraphs.add("end");
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (String l : outside) {
            sb.append(l).append("\n");
        }
        for (String l : subgraphs) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    @ReflectiveAccess
    public record Body(String mermaid, int subTopologies) { }
}
