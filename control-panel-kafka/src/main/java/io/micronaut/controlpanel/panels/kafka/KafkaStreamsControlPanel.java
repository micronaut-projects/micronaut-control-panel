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
                // Parse Step X: Type (params)
                int firstColon = line.indexOf(':');
                if (firstColon > 0) {
                    String stepNum = line.substring(0, firstColon).trim();
                    String rest = line.substring(firstColon + 1).trim();
                    int paren = rest.indexOf('(');
                    String typeAndName = paren > 0 ? rest.substring(0, paren).trim() : rest;
                    String name = typeAndName.substring(typeAndName.lastIndexOf(' ') + 1).trim();
                    if (!name.isEmpty()) {
                        nodes.add(name);
                        if (current != null) {
                            edges.add(sanitizeId(current) + "-->" + sanitizeId(name));
                        }
                        current = name;
                    }
                    // Parse params for topics if Source or Sink
                    if (paren > 0) {
                        String params = rest.substring(paren);
                        if (params.contains("topics=")) {
                            int lb = params.indexOf('[');
                            int rb = params.indexOf(']', lb);
                            if (lb > 0 && rb > lb) {
                                String topicsStr = params.substring(lb + 1, rb);
                                for (String t : topicsStr.split(",")) {
                                    String topic = t.trim();
                                    if (!topic.isEmpty()) {
                                        nodes.add(topic);
                                        if (typeAndName.startsWith("Source")) {
                                            edges.add(sanitizeId(topic) + "-->" + sanitizeId(current));
                                        } else if (typeAndName.startsWith("Sink")) {
                                            edges.add(sanitizeId(current) + "-->" + sanitizeId(topic));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (line.contains("->")) {
                // Handle connections like "Step 0 -> Step 1"
                String[] parts = line.split("->");
                if (parts.length == 2) {
                    String from = parts[0].trim();
                    String to = parts[1].trim();
                    if (!from.isEmpty() && !to.isEmpty()) {
                        // Extract step names from "Step X"
                        String fromName = from.substring(from.lastIndexOf(' ') + 1).trim();
                        String toName = to.substring(to.lastIndexOf(' ') + 1).trim();
                        if (!fromName.isEmpty() && !toName.isEmpty()) {
                            nodes.add(fromName);
                            nodes.add(toName);
                            edges.add(sanitizeId(fromName) + "-->" + sanitizeId(toName));
                            current = toName;
                        }
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
