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

import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.admin.AdminClient;

/**
 * Control panel entry point for read-only Kafka cluster inspection.
 */
@Singleton
@Requires(beans = AdminClient.class)
@Requires(property = KafkaClusterControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public final class KafkaClusterControlPanel extends AbstractControlPanel<KafkaClusterControlPanel.Body> {

    public static final String NAME = "kafka-cluster";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";

    private static final Body BODY = new Body(KafkaClusterController.PATH);

    public KafkaClusterControlPanel(@Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
    }

    @Override
    public Body getBody() {
        return BODY;
    }

    @Override
    public String getTitle() {
        return "Kafka Cluster";
    }

    @Override
    public ControlPanel.Category getCategory() {
        return KafkaStreamsControlPanel.CATEGORY;
    }

    @Override
    public String getBadge() {
        return "read-only";
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record Body(String controllerPath) {
    }
}
