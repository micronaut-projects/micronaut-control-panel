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
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lists Kafka listeners and clients discovered in the Micronaut bean context, and
 * renders a Kafka Streams topology diagram when available.
 */
@Singleton
@Refreshable
@Requires(property = KafkaControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class KafkaControlPanel extends AbstractControlPanel<KafkaControlPanel.Body> {

    public static final String NAME = "kafka";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "Kafka", "fa-diagram-project");

    private final Body body;

    public KafkaControlPanel(BeanContext beanContext,
                             @Named(NAME) ControlPanelConfiguration configuration,
                             List<ConfiguredStreamBuilder> streamBuilders) {
        super(NAME, configuration);

        var listeners = beanContext.getAllBeanDefinitions().stream()
            .filter(bd -> bd.getAnnotationMetadata().hasAnnotation("io.micronaut.kafka.annotation.KafkaListener")
                || bd.getAnnotationMetadata().hasAnnotation("io.micronaut.configuration.kafka.annotation.KafkaListener"))
            .map(bd -> Map.<String, Object>of(
                "type", "listener",
                "class", bd.getBeanType().getName()
            ))
            .collect(Collectors.toList());

        var clients = beanContext.getAllBeanDefinitions().stream()
            .filter(bd -> bd.getAnnotationMetadata().hasAnnotation("io.micronaut.kafka.annotation.KafkaClient")
                || bd.getAnnotationMetadata().hasAnnotation("io.micronaut.configuration.kafka.annotation.KafkaClient"))
            .map(bd -> Map.<String, Object>of(
                "type", "client",
                "class", bd.getBeanType().getName()
            ))
            .collect(Collectors.toList());

        String topologyDescription = null;
        for (ConfiguredStreamBuilder builder : streamBuilders) {
            try {
                var topology = builder.build(builder.getConfiguration());
                if (topology != null) {
                    topologyDescription = topology.describe().toString();
                    break;
                }
            } catch (Throwable ignored) {
                // ignore builder failures
            }
        }
        String mermaid = generateMermaidFromDescription(topologyDescription);
        this.body = new Body(listeners, clients, mermaid);
    }

    @Override
    public Body getBody() {
        return body;
    }

    @Override
    public String getBadge() {
        int total = body.listeners().size() + body.clients().size();
        return String.valueOf(total);
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

    public record Body(List<Map<String, Object>> listeners, List<Map<String, Object>> clients, String mermaid) { }
}
