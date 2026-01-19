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
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category(NAME, "Kafka", "si si-apachekafka");

    private final Body body;

    public KafkaControlPanel(BeanContext beanContext,
                             @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        var listeners = beanContext.getAllBeanDefinitions().stream()
            .filter(bd -> bd.getAnnotationMetadata().hasAnnotation("io.micronaut.configuration.kafka.annotation.KafkaListener"))
            .map(bd -> Map.<String, Object>of(
                "type", "listener",
                "class", bd.getBeanType().getName()
            ))
            .toList();

        var clients = beanContext.getAllBeanDefinitions().stream()
            .filter(bd -> bd.getAnnotationMetadata().hasAnnotation("io.micronaut.configuration.kafka.annotation.KafkaClient"))
            .map(bd -> Map.<String, Object>of(
                "type", "client",
                "class", bd.getBeanType().getName()
            ))
            .toList();

        this.body = new Body(listeners, clients, null);
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

    public record Body(List<Map<String, Object>> listeners, List<Map<String, Object>> clients, String mermaid) { }
}
