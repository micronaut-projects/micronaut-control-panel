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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import org.jspecify.annotations.Nullable;

/**
 * Default-off Kafka write safety configuration for the Kafka cluster panel.
 */
@Internal
@ConfigurationProperties(KafkaClusterWriteConfiguration.PREFIX)
record KafkaClusterWriteConfiguration(
    @Bindable(defaultValue = StringUtils.FALSE)
    boolean enabled,
    @Bindable(defaultValue = StringUtils.FALSE)
    boolean destructiveEnabled,
    @Bindable(defaultValue = "1048576")
    int maxMessageValueBytes,
    @Bindable(defaultValue = "1024")
    int maxMessageKeyBytes,
    @Bindable(defaultValue = "20")
    int maxMessageHeaders,
    @Bindable(defaultValue = "256")
    int maxMessageHeaderKeyBytes,
    @Bindable(defaultValue = "4096")
    int maxMessageHeaderValueBytes,
    @Nullable
    Actions actions
) implements Toggleable {

    static final String PREFIX = ControlPanelConfiguration.PREFIX + ".kafka.writes";

    KafkaClusterWriteConfiguration() {
        this(false, false, 1024 * 1024, 1024, 20, 256, 4096, new Actions());
    }

    KafkaClusterWriteConfiguration {
        if (actions == null) {
            actions = new Actions();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    boolean isDestructiveEnabled() {
        return destructiveEnabled;
    }

    int getMaxMessageValueBytes() {
        return maxMessageValueBytes;
    }

    int getMaxMessageKeyBytes() {
        return maxMessageKeyBytes;
    }

    int getMaxMessageHeaders() {
        return maxMessageHeaders;
    }

    int getMaxMessageHeaderKeyBytes() {
        return maxMessageHeaderKeyBytes;
    }

    int getMaxMessageHeaderValueBytes() {
        return maxMessageHeaderValueBytes;
    }

    boolean actionEnabled(String action) {
        return switch (action) {
            case "topics.create" -> actions.isCreateTopic();
            case "topics.update-config" -> actions.isUpdateTopicConfig();
            case "topics.increase-partitions" -> actions.isIncreasePartitions();
            case "topics.delete" -> actions.isDeleteTopic();
            case "messages.produce" -> actions.isProduceMessage();
            case "consumer-groups.delete" -> actions.isDeleteConsumerGroup();
            case "consumer-groups.reset-offsets" -> actions.isResetConsumerGroupOffsets();
            case "app-consumers.pause" -> actions.isPauseAppConsumer();
            case "app-consumers.resume" -> actions.isResumeAppConsumer();
            case "schema-registry.register" -> actions.isRegisterSchema();
            case "schema-registry.update-compatibility" -> actions.isUpdateSchemaCompatibility();
            case "schema-registry.delete-subject" -> actions.isDeleteSchemaSubject();
            case "schema-registry.delete-version" -> actions.isDeleteSchemaVersion();
            case "kafka-connect.pause" -> actions.isPauseConnector();
            case "kafka-connect.resume" -> actions.isResumeConnector();
            case "kafka-connect.restart" -> actions.isRestartConnector();
            case "kafka-connect.restart-task" -> actions.isRestartConnectorTask();
            case "kafka-connect.update-config" -> actions.isUpdateConnectorConfig();
            case "kafka-connect.delete" -> actions.isDeleteConnector();
            default -> false;
        };
    }

    /**
     * Per-action write toggles. Actions default to true so the top-level write gate remains the main safety switch.
     */
    record Actions(
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean createTopic,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean updateTopicConfig,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean increasePartitions,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean deleteTopic,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean produceMessage,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean deleteConsumerGroup,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean resetConsumerGroupOffsets,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean pauseAppConsumer,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean resumeAppConsumer,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean registerSchema,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean updateSchemaCompatibility,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean deleteSchemaSubject,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean deleteSchemaVersion,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean pauseConnector,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean resumeConnector,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean restartConnector,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean restartConnectorTask,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean updateConnectorConfig,
        @Bindable(defaultValue = StringUtils.TRUE)
        boolean deleteConnector
    ) {

        Actions() {
            this(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);
        }

        boolean isCreateTopic() {
            return createTopic;
        }

        boolean isUpdateTopicConfig() {
            return updateTopicConfig;
        }

        boolean isIncreasePartitions() {
            return increasePartitions;
        }

        boolean isDeleteTopic() {
            return deleteTopic;
        }

        boolean isProduceMessage() {
            return produceMessage;
        }

        boolean isDeleteConsumerGroup() {
            return deleteConsumerGroup;
        }

        boolean isResetConsumerGroupOffsets() {
            return resetConsumerGroupOffsets;
        }

        boolean isPauseAppConsumer() {
            return pauseAppConsumer;
        }

        boolean isResumeAppConsumer() {
            return resumeAppConsumer;
        }

        boolean isRegisterSchema() {
            return registerSchema;
        }

        boolean isUpdateSchemaCompatibility() {
            return updateSchemaCompatibility;
        }

        boolean isDeleteSchemaSubject() {
            return deleteSchemaSubject;
        }

        boolean isDeleteSchemaVersion() {
            return deleteSchemaVersion;
        }

        boolean isPauseConnector() {
            return pauseConnector;
        }

        boolean isResumeConnector() {
            return resumeConnector;
        }

        boolean isRestartConnector() {
            return restartConnector;
        }

        boolean isRestartConnectorTask() {
            return restartConnectorTask;
        }

        boolean isUpdateConnectorConfig() {
            return updateConnectorConfig;
        }

        boolean isDeleteConnector() {
            return deleteConnector;
        }
    }
}
