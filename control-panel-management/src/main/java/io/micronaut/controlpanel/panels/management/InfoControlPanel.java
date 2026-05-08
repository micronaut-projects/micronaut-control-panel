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
package io.micronaut.controlpanel.panels.management;

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.management.endpoint.info.InfoAggregator;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Control panel that displays information from the application info endpoint.
 *
 * @since 2.0.0
 */
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = InfoControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class InfoControlPanel extends AbstractControlPanel<InfoControlPanel.Body> {

    public static final String NAME = InfoEndpoint.NAME;
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final InfoAggregator<Map<String, Object>> infoAggregator;
    private final InfoSource[] infoSources;

    public InfoControlPanel(InfoAggregator<Map<String, Object>> infoAggregator,
                            InfoSource[] infoSources,
                            @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.infoAggregator = infoAggregator;
        this.infoSources = infoSources;
    }

    @Override
    public Body getBody() {
        Map<String, Object> info = Mono.from(infoAggregator.aggregate(infoSources)).block();
        if (info == null || info.isEmpty()) {
            return new Body(List.of(), List.of(), List.of());
        }
        List<InfoNode> sections = toNodes(info);
        return new Body(sections, toRows(sections), toSummaryItems(info));
    }

    private static List<InfoNode> toNodes(Map<String, Object> values) {
        List<InfoNode> nodes = new ArrayList<>(values.size());
        values.forEach((name, value) -> nodes.add(toNode(name, value)));
        return nodes;
    }

    private static InfoNode toNode(String name, Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> values.put(String.valueOf(key), mapValue));
            return new InfoNode(name, displayName(name), emptyValue(values), toNodes(values));
        }
        if (value instanceof Iterable<?> iterable) {
            List<InfoNode> children = new ArrayList<>();
            int index = 0;
            for (Object element : iterable) {
                children.add(toNode(String.valueOf(index), element));
                index++;
            }
            return new InfoNode(name, displayName(name), emptyValue(children), children);
        }
        return new InfoNode(name, displayName(name), String.valueOf(value), List.of());
    }

    private static String emptyValue(Map<String, Object> values) {
        return values.isEmpty() ? "(empty)" : null;
    }

    private static String emptyValue(List<InfoNode> values) {
        return values.isEmpty() ? "(empty)" : null;
    }

    private static String displayName(String name) {
        if (name.chars().allMatch(Character::isDigit)) {
            return "[" + name + "]";
        }
        String normalized = name.replace('-', ' ').replace('_', ' ').replace('.', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (i > 0 && Character.isUpperCase(current) && Character.isLowerCase(normalized.charAt(i - 1))) {
                result.append(' ');
            }
            result.append(current);
        }
        String[] words = result.toString().trim().split("\\s+");
        StringBuilder title = new StringBuilder(result.length());
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!title.isEmpty()) {
                    title.append(' ');
                }
                title.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    title.append(word.substring(1).toLowerCase(Locale.ENGLISH));
                }
            }
        }
        return title.toString();
    }

    private static List<InfoRow> toRows(List<InfoNode> sections) {
        List<InfoRow> rows = new ArrayList<>();
        for (InfoNode section : sections) {
            addRows(rows, section, section.displayName(), null, 0);
        }
        return rows;
    }

    private static void addRows(List<InfoRow> rows, InfoNode node, String section, String key, int depth) {
        String rowKey = key == null ? node.displayName() : key;
        if (node.value() != null) {
            rows.add(new InfoRow(section, rowKey, node.value(), depth));
        }
        for (InfoNode child : node.children()) {
            String childKey = key == null ? child.displayName() : key + " / " + child.displayName();
            addRows(rows, child, section, childKey, depth + 1);
        }
    }

    private static List<SummaryItem> toSummaryItems(Map<String, Object> info) {
        List<SummaryItem> items = new ArrayList<>();
        addSummaryItem(items, "Name", false, valueAt(info, "application.name", "app.name", "demo.name", "build.name"));
        addSummaryItem(items, "Version", false, valueAt(info, "application.version", "app.version", "build.version", "version"));
        addSummaryItem(items, "Commit", true, valueAt(info, "git.commit.id.abbrev", "git.commit.id", "git.commit"));
        addSummaryItem(items, "Branch", true, valueAt(info, "git.branch"));
        addSummaryItem(items, "Build time", false, valueAt(info, "build.time", "build.timestamp"));
        return items;
    }

    private static void addSummaryItem(List<SummaryItem> items, String label, boolean code, Object value) {
        if (value != null) {
            items.add(new SummaryItem(label, String.valueOf(value), code));
        }
    }

    private static Object valueAt(Map<String, Object> info, String... paths) {
        for (String path : paths) {
            Object value = valueAt(info, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object valueAt(Map<String, Object> info, String path) {
        Object current = info;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current instanceof Map<?, ?> || current instanceof Iterable<?> ? null : current;
    }

    /**
     * Represents the body of this control panel.
     *
     * @param sections the top-level info sections
     * @param rows flattened scalar info rows
     * @param summaryItems common high-value metadata rows
     */
    @ReflectiveAccess
    public record Body(List<InfoNode> sections, List<InfoRow> rows, List<SummaryItem> summaryItems) { }

    /**
     * Represents a single info node rendered by the recursive templates.
     *
     * @param name the original node name
     * @param displayName the display name
     * @param value the scalar value
     * @param children nested child nodes
     */
    @ReflectiveAccess
    public record InfoNode(String name, String displayName, String value, List<InfoNode> children) { }

    /**
     * Represents a flattened info row rendered by the detail template.
     *
     * @param section the top-level info section
     * @param key the nested key path inside the section
     * @param value the scalar value
     * @param depth the nesting depth
     */
    @ReflectiveAccess
    public record InfoRow(String section, String key, String value, int depth) { }

    /**
     * Represents a high-value metadata item rendered in the summary.
     *
     * @param label the summary label
     * @param value the summary value
     * @param code whether to render the value as code
     */
    @ReflectiveAccess
    public record SummaryItem(String label, String value, boolean code) { }
}
