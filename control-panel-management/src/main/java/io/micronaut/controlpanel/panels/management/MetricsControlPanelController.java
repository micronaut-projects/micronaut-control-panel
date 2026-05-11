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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Internal API used by the Metrics control panel detail view.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Internal
@Controller("${micronaut.control-panel.path:/control-panel}/api/metrics")
@ExecuteOn(TaskExecutors.BLOCKING)
@Requires(beans = MetricsEndpoint.class)
@Requires(property = MetricsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public final class MetricsControlPanelController {

    private static final String MESSAGE = "message";

    private final MetricsEndpoint endpoint;
    private final MeterRegistry meterRegistry;

    public MetricsControlPanelController(MetricsEndpoint endpoint, MeterRegistry meterRegistry) {
        this.endpoint = endpoint;
        this.meterRegistry = meterRegistry;
    }

    @Get(produces = MediaType.APPLICATION_JSON)
    public HttpResponse<Map<String, Object>> detail(@Nullable @QueryValue String name,
                                                    @Nullable @QueryValue("tag") List<String> tag) {
        if (name == null || name.isEmpty()) {
            return HttpResponse.badRequest(Map.of(MESSAGE, "Metric name is required"));
        }
        String metricName = name;
        if (!endpoint.listNames().getNames().contains(metricName)) {
            return HttpResponse.notFound(Map.of(MESSAGE, "Metric not found"));
        }
        var parsedTags = parseTags(tag == null ? List.of() : tag);
        if (parsedTags == null) {
            return HttpResponse.badRequest(Map.of(MESSAGE, "Tags must be in the form key:value"));
        }
        Collection<Meter> meters = meterRegistry.find(metricName).tags(parsedTags).meters();
        if (meters.isEmpty()) {
            return HttpResponse.notFound(Map.of(MESSAGE, "Metric not found for the supplied tags"));
        }
        Meter.Id id = meters.iterator().next().getId();
        return HttpResponse.ok(Map.of(
            "name", metricName,
            "description", nullable(id.getDescription()),
            "baseUnit", nullable(id.getBaseUnit()),
            "measurements", measurements(meters),
            "availableTags", availableTags(meters)
        ));
    }

    private static String nullable(@Nullable String value) {
        return value == null ? "" : value;
    }

    @Nullable
    private static List<Tag> parseTags(List<String> tag) {
        return tag.stream()
            .map(MetricsControlPanelController::parseTag)
            .collect(Collectors.collectingAndThen(Collectors.toList(), tags -> tags.contains(null) ? null : tags));
    }

    @Nullable
    private static Tag parseTag(String tag) {
        int separator = tag.indexOf(':');
        if (separator < 1 || separator == tag.length() - 1) {
            return null;
        }
        return Tag.of(tag.substring(0, separator), tag.substring(separator + 1));
    }

    private static List<Map<String, Object>> measurements(Collection<Meter> meters) {
        Map<Statistic, Double> samples = new LinkedHashMap<>();
        for (Meter meter : meters) {
            for (var measurement : meter.measure()) {
                samples.merge(measurement.getStatistic(), measurement.getValue(), mergeFunction(measurement.getStatistic()));
            }
        }
        return samples.entrySet()
            .stream()
            .map(entry -> {
                Map<String, Object> sample = new LinkedHashMap<>();
                sample.put("statistic", entry.getKey().toString());
                sample.put("value", entry.getValue());
                return sample;
            })
            .toList();
    }

    private static BiFunction<Double, Double, Double> mergeFunction(Statistic statistic) {
        return Statistic.MAX.equals(statistic) ? Double::max : Double::sum;
    }

    private static List<Map<String, Object>> availableTags(Collection<Meter> meters) {
        Map<String, Set<String>> tags = new HashMap<>();
        for (Meter meter : meters) {
            for (Tag tag : meter.getId().getTags()) {
                tags.computeIfAbsent(tag.getKey(), ignored -> new TreeSet<>()).add(tag.getValue());
            }
        }
        return tags.entrySet()
            .stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(entry -> {
                Map<String, Object> availableTag = new LinkedHashMap<>();
                availableTag.put("tag", entry.getKey());
                availableTag.put("values", entry.getValue());
                return availableTag;
            })
            .toList();
    }
}
