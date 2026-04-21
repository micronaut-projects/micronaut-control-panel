/*
 * Copyright 2017-2025 original authors
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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only control panel for browsing Micrometer metrics.
 *
 * @author Micronaut Engineer
 * @since 2.0.0
 */
@Singleton
@Requires(beans = MetricsEndpoint.class)
@Requires(property = MetricsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class MetricsControlPanel extends AbstractControlPanel<MetricsControlPanel.Body> {

    static final int DASHBOARD_PREVIEW_LIMIT = 5;

    public static final String NAME = "metrics";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private final MetricsEndpoint endpoint;

    public MetricsControlPanel(MetricsEndpoint endpoint, @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.endpoint = endpoint;
    }

    @Override
    public Body getBody() {
        List<String> metricNames = getMetricNames(endpoint);
        return new Body(
            metricNames,
            metricNames.stream().limit(DASHBOARD_PREVIEW_LIMIT).toList(),
            metricNames.isEmpty() ? null : metricNames.get(0)
        );
    }

    @Override
    public String getBadge() {
        return String.valueOf(getMetricNames(endpoint).size());
    }

    static List<String> getMetricNames(MetricsEndpoint endpoint) {
        MetricsEndpoint.MetricNames metricNames = endpoint.listNames();
        if (metricNames == null || metricNames.getNames() == null) {
            return List.of();
        }
        return List.copyOf(metricNames.getNames());
    }

    static @Nullable MetricDetail toMetricDetail(MetricsEndpoint.MetricDetails details, @Nullable List<String> selectedTags) {
        if (details == null) {
            return null;
        }
        List<String> normalizedSelectedTags = selectedTags == null ? List.of() : List.copyOf(selectedTags);
        List<MetricMeasurement> measurements = details.getMeasurements().stream()
            .map(sample -> new MetricMeasurement(sample.getStatistic().name(), sample.getValue()))
            .sorted(Comparator.comparing(MetricMeasurement::statistic))
            .toList();
        List<MetricAvailableTag> availableTags = details.getAvailableTags().stream()
            .map(tag -> new MetricAvailableTag(
                tag.getTag(),
                tag.getValues().stream().sorted().collect(Collectors.toList())
            ))
            .sorted(Comparator.comparing(MetricAvailableTag::tag))
            .toList();
        return new MetricDetail(
            details.getName(),
            details.getDescription(),
            details.getBaseUnit(),
            measurements,
            availableTags,
            normalizedSelectedTags
        );
    }

    static @Nullable MetricDetail toMetricDetail(String metricName, Collection<Meter> meters, @Nullable List<String> selectedTags) {
        if (meters.isEmpty()) {
            return null;
        }

        Meter.Id meterId = meters.iterator().next().getId();
        Map<Statistic, Double> aggregatedMeasurements = new LinkedHashMap<>();
        Map<String, Set<String>> availableTagsByName = new LinkedHashMap<>();
        for (Meter meter : meters) {
            mergeMeasurements(aggregatedMeasurements, meter.measure());
            mergeAvailableTags(availableTagsByName, meter);
        }

        List<String> normalizedSelectedTags = selectedTags == null ? List.of() : List.copyOf(selectedTags);
        normalizedSelectedTags.stream()
            .map(MetricsControlPanel::tagName)
            .forEach(availableTagsByName::remove);

        List<MetricMeasurement> measurements = aggregatedMeasurements.entrySet().stream()
            .map(entry -> new MetricMeasurement(entry.getKey().name(), entry.getValue()))
            .sorted(Comparator.comparing(MetricMeasurement::statistic))
            .toList();
        List<MetricAvailableTag> availableTags = availableTagsByName.entrySet().stream()
            .map(entry -> new MetricAvailableTag(
                entry.getKey(),
                entry.getValue().stream().sorted().collect(Collectors.toList())
            ))
            .sorted(Comparator.comparing(MetricAvailableTag::tag))
            .toList();

        return new MetricDetail(
            metricName,
            meterId.getDescription(),
            meterId.getBaseUnit(),
            measurements,
            availableTags,
            normalizedSelectedTags
        );
    }

    private static void mergeMeasurements(Map<Statistic, Double> measurements, Iterable<Measurement> source) {
        for (Measurement measurement : source) {
            measurements.merge(
                measurement.getStatistic(),
                measurement.getValue(),
                measurement.getStatistic() == Statistic.MAX ? Math::max : Double::sum
            );
        }
    }

    private static void mergeAvailableTags(Map<String, Set<String>> availableTagsByName, Meter meter) {
        for (io.micrometer.core.instrument.Tag tag : meter.getId().getTags()) {
            availableTagsByName.computeIfAbsent(tag.getKey(), ignored -> new java.util.LinkedHashSet<>()).add(tag.getValue());
        }
    }

    private static String tagName(String tag) {
        int separatorIndex = tag.indexOf(':');
        return separatorIndex >= 0 ? tag.substring(0, separatorIndex) : tag;
    }

    /**
     * Data used by the dashboard card and detail page.
     *
     * @param metricNames all available metric names, ordered alphabetically
     * @param previewMetricNames compact preview used on the dashboard card
     * @param initialMetricName initial metric selected by the detail page
     */
    @ReflectiveAccess
    public record Body(List<String> metricNames,
                       List<String> previewMetricNames,
                       @Nullable String initialMetricName) { }

    /**
     * Serialized metric details returned to the detail page.
     *
     * @param name metric name
     * @param description metric description
     * @param baseUnit metric base unit
     * @param measurements current samples
     * @param availableTags remaining available tags
     * @param selectedTags currently applied tag filters
     */
    @ReflectiveAccess
    @Serdeable
    public record MetricDetail(String name,
                               @Nullable String description,
                               @Nullable String baseUnit,
                               List<MetricMeasurement> measurements,
                               List<MetricAvailableTag> availableTags,
                               List<String> selectedTags) { }

    /**
     * A single metric sample.
     *
     * @param statistic statistic name
     * @param value current value
     */
    @ReflectiveAccess
    @Serdeable
    public record MetricMeasurement(String statistic, Double value) { }

    /**
     * Remaining values that can be used to refine a metric lookup.
     *
     * @param tag tag name
     * @param values allowed values for the tag
     */
    @ReflectiveAccess
    @Serdeable
    public record MetricAvailableTag(String tag, List<String> values) { }
}
