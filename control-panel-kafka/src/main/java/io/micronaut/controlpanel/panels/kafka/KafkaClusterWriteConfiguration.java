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
 * Configures the write capabilities exposed by the Kafka cluster panel.
 *
 * @param enabled Enables non-destructive Kafka write actions such as producing records and updating mutable resources.
 * @param destructiveEnabled Enables destructive Kafka write actions such as deleting topics, consumer groups, connectors, or schemas.
 * @param maxMessageValueBytes Maximum number of bytes allowed for a produced record value.
 * @param maxMessageKeyBytes Maximum number of bytes allowed for a produced record key.
 * @param maxMessageHeaders Maximum number of headers allowed on a produced record.
 * @param maxMessageHeaderKeyBytes Maximum number of bytes allowed for each produced record header key.
 * @param maxMessageHeaderValueBytes Maximum number of bytes allowed for each produced record header value.
 * @param actions Per-action Kafka write toggles that can disable individual write operations after the top-level write gate is enabled.
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
     * Configures individual Kafka write actions after the top-level write gate is enabled.
     *
     * @param createTopic Enables creating Kafka topics from the control panel.
     * @param updateTopicConfig Enables updating Kafka topic configuration entries.
     * @param increasePartitions Enables increasing the partition count for Kafka topics.
     * @param deleteTopic Enables deleting Kafka topics.
     * @param produceMessage Enables producing records to Kafka topics.
     * @param deleteConsumerGroup Enables deleting Kafka consumer groups.
     * @param resetConsumerGroupOffsets Enables resetting committed offsets for Kafka consumer groups.
     * @param pauseAppConsumer Enables pausing application-managed Kafka consumers.
     * @param resumeAppConsumer Enables resuming application-managed Kafka consumers.
     * @param registerSchema Enables registering Schema Registry schemas.
     * @param updateSchemaCompatibility Enables updating Schema Registry compatibility settings.
     * @param deleteSchemaSubject Enables deleting Schema Registry subjects.
     * @param deleteSchemaVersion Enables deleting Schema Registry schema versions.
     * @param pauseConnector Enables pausing Kafka Connect connectors.
     * @param resumeConnector Enables resuming Kafka Connect connectors.
     * @param restartConnector Enables restarting Kafka Connect connectors.
     * @param restartConnectorTask Enables restarting Kafka Connect connector tasks.
     * @param updateConnectorConfig Enables updating Kafka Connect connector configurations.
     * @param deleteConnector Enables deleting Kafka Connect connectors.
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
