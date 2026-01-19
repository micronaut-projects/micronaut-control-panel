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
import jakarta.inject.Named;

/**
 * A per-streams control panel that renders the Kafka Streams topology for each configured builder.
 */
@EachBean(ConfiguredStreamBuilder.class)
public class KafkaStreamsControlPanel extends AbstractEachBeanControlPanel<KafkaStreamsControlPanel.Body> {

    public static final String NAME = "kafkastreams";
    private static final ControlPanel.Category CATEGORY = new ControlPanel.Category("kafka", "Kafka", "si si-apachekafka");

    private final String beanName;
    private final Body body;

    public KafkaStreamsControlPanel(@Parameter String beanName,
                                    ConfiguredStreamBuilder builder,
                                    @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        String mermaid = generateMermaidFromDescription(builder.build(builder.getConfiguration()).describe().toString());
        this.body = new Body(mermaid);
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
    public String getIcon() {
        return "si si-apachekafka";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    private static String sanitizeId(String s) {
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String generateMermaidFromDescription(String desc) {
        if (desc == null || desc.isBlank()) {
            return "flowchart LR\nEMPTY[No topology detected]";
        }
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        java.util.Set<String> nodes = new java.util.HashSet<>();
        java.util.Set<String> edges = new java.util.HashSet<>();
        String current = null;
        for (String raw : desc.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("Source:")) {
                int nameStart = line.indexOf(':') + 1;
                int paren = line.indexOf('(');
                String name = line.substring(nameStart, paren > 0 ? paren : line.length()).trim();
                current = name;
                nodes.add(name);
                int topicsIdx = line.indexOf("topics:");
                if (topicsIdx >= 0) {
                    int lb = line.indexOf('[', topicsIdx);
                    int rb = line.indexOf(']', lb);
                    if (lb > 0 && rb > lb) {
                        String topics = line.substring(lb + 1, rb);
                        for (String t : topics.split(",")) {
                            String topic = t.trim();
                            if (!topic.isEmpty()) {
                                nodes.add(topic);
                                edges.add(sanitizeId(topic) + "-->" + sanitizeId(name));
                            }
                        }
                    }
                }
            } else if (line.startsWith("Processor:")) {
                int nameStart = line.indexOf(':') + 1;
                int paren = line.indexOf('(');
                String name = line.substring(nameStart, paren > 0 ? paren : line.length()).trim();
                current = name;
                nodes.add(name);
            } else if (line.startsWith("Sink:")) {
                int nameStart = line.indexOf(':') + 1;
                int paren = line.indexOf('(');
                String name = line.substring(nameStart, paren > 0 ? paren : line.length()).trim();
                nodes.add(name);
                int topicIdx = line.indexOf("topic:");
                if (topicIdx >= 0) {
                    int end = line.indexOf(')', topicIdx);
                    String topic = line.substring(topicIdx + "topic:".length(), end > 0 ? end : line.length()).trim();
                    nodes.add(topic);
                    edges.add(sanitizeId(name) + "-->" + sanitizeId(topic));
                }
                current = name;
            } else if (line.contains("->")) {
                String[] parts = line.split(">\\s*");
                String rhs = parts[parts.length - 1].trim();
                if (current != null && !rhs.isEmpty()) {
                    edges.add(sanitizeId(current) + "-->" + sanitizeId(rhs));
                    nodes.add(rhs);
                }
            }
        }
        for (String n : nodes) {
            sb.append(sanitizeId(n)).append("[").append(n).append("]\n");
        }
        for (String e : edges) {
            sb.append(e).append("\n");
        }
        return sb.toString();
    }

    public record Body(String mermaid) { }
}
