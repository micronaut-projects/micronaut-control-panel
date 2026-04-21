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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Internal helper controller for metric drill-down requests from the control panel detail page.
 *
 * @author Micronaut Engineer
 * @since 2.0.0
 */
@Internal
@Controller("${" + ControlPanelModuleConfiguration.PROPERTY_PATH + ":" + ControlPanelModuleConfiguration.DEFAULT_PATH + "}/metrics")
@ExecuteOn(TaskExecutors.BLOCKING)
@Requires(beans = { MetricsEndpoint.class, MetricsControlPanel.class })
@Requires(property = MetricsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public class MetricsControlPanelController {

    private final MeterRegistry meterRegistry;

    public MetricsControlPanelController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Get("/details/{metricName}")
    public HttpResponse<MetricsControlPanel.MetricDetail> detail(String metricName,
                                                                 @Nullable @QueryValue List<String> tag) {
        List<Tag> selectedTags = tag == null ? List.of() : tag.stream()
            .map(MetricsControlPanelController::toTag)
            .toList();
        MetricsControlPanel.MetricDetail details =
            MetricsControlPanel.toMetricDetail(metricName, meterRegistry.find(metricName).tags(selectedTags).meters(), tag);
        return details == null ? HttpResponse.notFound() : HttpResponse.ok(details);
    }

    private static Tag toTag(String tag) {
        int separatorIndex = tag.indexOf(':');
        if (separatorIndex < 0) {
            throw new UnsatisfiedArgumentException(
                Argument.of(List.class, "tags"),
                "Tags must be in the form key:value"
            );
        }
        return Tag.of(tag.substring(0, separatorIndex), tag.substring(separatorIndex + 1));
    }
}
