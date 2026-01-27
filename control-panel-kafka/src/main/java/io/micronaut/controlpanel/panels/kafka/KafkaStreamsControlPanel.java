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

import static io.micronaut.controlpanel.panels.kafka.KafkaControlPanel.CATEGORY;

/**
 * A per-streams control panel that renders the Kafka Streams topology for each configured builder.
 */
@EachBean(ConfiguredStreamBuilder.class)
public class KafkaStreamsControlPanel extends AbstractEachBeanControlPanel<KafkaStreamsControlPanel.Body> {

    public static final String NAME = "kafka-streams";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamsControlPanel.class);

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
            if (line.startsWith("Step ")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String step = line.substring(0, colon).trim();
                    int typeStart = line.indexOf(':', colon + 1);
                    if (typeStart > 0) {
                        String type = line.substring(colon + 1, typeStart).trim();
                        int nameStart = line.indexOf(':', typeStart + 1);
                        if (nameStart > 0) {
                            int paren = line.indexOf('(');
                            String name = line.substring(nameStart + 1, paren > 0 ? paren : line.length()).trim();
                            if (!name.isEmpty()) {
                                nodes.add(name);
                                if (current != null) {
                                    edges.add(sanitizeId(current) + "-->" + sanitizeId(name));
                                }
                                current = name;
                            }
                            int parenStart = line.indexOf('(');
                            if (parenStart > 0) {
                                int closeParen = line.indexOf(')', parenStart);
                                if (closeParen > parenStart) {
                                    String parenContent = line.substring(parenStart + 1, closeParen);
                                    if (type.equals("Source") || type.equals("Sink")) {
                                        int topicsIdx = line.indexOf("topics=");
                                        if (topicsIdx > 0) {
                                            int lb = line.indexOf('[', topicsIdx);
                                            int rb = line.indexOf(']', lb);
                                            if (lb > 0 && rb > lb) {
                                                String topicsStr = line.substring(lb + 1, rb);
                                                for (String t : topicsStr.split(",")) {
                                                    String topic = t.trim();
                                                    if (!topic.isEmpty()) {
                                                        nodes.add(topic);
                                                        if (type.equals("Source")) {
                                                            edges.add(sanitizeId(topic) + "-->" + sanitizeId(current));
                                                        } else {
                                                            edges.add(sanitizeId(current) + "-->" + sanitizeId(topic));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (line.contains("->")) {
                String[] parts = line.split("->");
                if (parts.length == 2) {
                    String from = parts[0].trim();
                    String to = parts[1].trim();
                    if (!from.isEmpty() && !to.isEmpty()) {
                        nodes.add(from);
                        nodes.add(to);
                        edges.add(sanitizeId(from) + "-->" + sanitizeId(to));
                        current = to;
                    }
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

    @ReflectiveAccess
    public record Body(String mermaid) { }
}
