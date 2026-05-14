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
import io.micronaut.controlpanel.core.config.ControlPanelModuleConfiguration;
import io.micronaut.core.annotation.Internal;

/**
 * Default-off Kafka write safety configuration for the Kafka cluster panel.
 */
@Internal
@ConfigurationProperties(KafkaClusterWriteConfiguration.PREFIX)
final class KafkaClusterWriteConfiguration {

    static final String PREFIX = ControlPanelModuleConfiguration.PREFIX + ".kafka.writes";

    private boolean enabled;
    private boolean destructiveEnabled;
    private int maxMessageValueBytes = 1024 * 1024;
    private int maxMessageKeyBytes = 1024;
    private int maxMessageHeaders = 20;
    private int maxMessageHeaderKeyBytes = 256;
    private int maxMessageHeaderValueBytes = 4096;
    private Actions actions = new Actions();

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    boolean isDestructiveEnabled() {
        return destructiveEnabled;
    }

    void setDestructiveEnabled(boolean destructiveEnabled) {
        this.destructiveEnabled = destructiveEnabled;
    }

    int getMaxMessageValueBytes() {
        return maxMessageValueBytes;
    }

    void setMaxMessageValueBytes(int maxMessageValueBytes) {
        this.maxMessageValueBytes = maxMessageValueBytes;
    }

    int getMaxMessageKeyBytes() {
        return maxMessageKeyBytes;
    }

    void setMaxMessageKeyBytes(int maxMessageKeyBytes) {
        this.maxMessageKeyBytes = maxMessageKeyBytes;
    }

    int getMaxMessageHeaders() {
        return maxMessageHeaders;
    }

    void setMaxMessageHeaders(int maxMessageHeaders) {
        this.maxMessageHeaders = maxMessageHeaders;
    }

    int getMaxMessageHeaderKeyBytes() {
        return maxMessageHeaderKeyBytes;
    }

    void setMaxMessageHeaderKeyBytes(int maxMessageHeaderKeyBytes) {
        this.maxMessageHeaderKeyBytes = maxMessageHeaderKeyBytes;
    }

    int getMaxMessageHeaderValueBytes() {
        return maxMessageHeaderValueBytes;
    }

    void setMaxMessageHeaderValueBytes(int maxMessageHeaderValueBytes) {
        this.maxMessageHeaderValueBytes = maxMessageHeaderValueBytes;
    }

    Actions getActions() {
        return actions;
    }

    void setActions(Actions actions) {
        this.actions = actions;
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
    static final class Actions {
        private boolean createTopic = true;
        private boolean updateTopicConfig = true;
        private boolean increasePartitions = true;
        private boolean deleteTopic = true;
        private boolean produceMessage = true;
        private boolean deleteConsumerGroup = true;
        private boolean resetConsumerGroupOffsets = true;
        private boolean pauseAppConsumer = true;
        private boolean resumeAppConsumer = true;
        private boolean registerSchema = true;
        private boolean updateSchemaCompatibility = true;
        private boolean deleteSchemaSubject = true;
        private boolean deleteSchemaVersion = true;
        private boolean pauseConnector = true;
        private boolean resumeConnector = true;
        private boolean restartConnector = true;
        private boolean restartConnectorTask = true;
        private boolean updateConnectorConfig = true;
        private boolean deleteConnector = true;

        boolean isCreateTopic() {
            return createTopic;
        }

        void setCreateTopic(boolean createTopic) {
            this.createTopic = createTopic;
        }

        boolean isUpdateTopicConfig() {
            return updateTopicConfig;
        }

        void setUpdateTopicConfig(boolean updateTopicConfig) {
            this.updateTopicConfig = updateTopicConfig;
        }

        boolean isIncreasePartitions() {
            return increasePartitions;
        }

        void setIncreasePartitions(boolean increasePartitions) {
            this.increasePartitions = increasePartitions;
        }

        boolean isDeleteTopic() {
            return deleteTopic;
        }

        void setDeleteTopic(boolean deleteTopic) {
            this.deleteTopic = deleteTopic;
        }

        boolean isProduceMessage() {
            return produceMessage;
        }

        void setProduceMessage(boolean produceMessage) {
            this.produceMessage = produceMessage;
        }

        boolean isDeleteConsumerGroup() {
            return deleteConsumerGroup;
        }

        void setDeleteConsumerGroup(boolean deleteConsumerGroup) {
            this.deleteConsumerGroup = deleteConsumerGroup;
        }

        boolean isResetConsumerGroupOffsets() {
            return resetConsumerGroupOffsets;
        }

        void setResetConsumerGroupOffsets(boolean resetConsumerGroupOffsets) {
            this.resetConsumerGroupOffsets = resetConsumerGroupOffsets;
        }

        boolean isPauseAppConsumer() {
            return pauseAppConsumer;
        }

        void setPauseAppConsumer(boolean pauseAppConsumer) {
            this.pauseAppConsumer = pauseAppConsumer;
        }

        boolean isResumeAppConsumer() {
            return resumeAppConsumer;
        }

        void setResumeAppConsumer(boolean resumeAppConsumer) {
            this.resumeAppConsumer = resumeAppConsumer;
        }

        boolean isRegisterSchema() {
            return registerSchema;
        }

        void setRegisterSchema(boolean registerSchema) {
            this.registerSchema = registerSchema;
        }

        boolean isUpdateSchemaCompatibility() {
            return updateSchemaCompatibility;
        }

        void setUpdateSchemaCompatibility(boolean updateSchemaCompatibility) {
            this.updateSchemaCompatibility = updateSchemaCompatibility;
        }

        boolean isDeleteSchemaSubject() {
            return deleteSchemaSubject;
        }

        void setDeleteSchemaSubject(boolean deleteSchemaSubject) {
            this.deleteSchemaSubject = deleteSchemaSubject;
        }

        boolean isDeleteSchemaVersion() {
            return deleteSchemaVersion;
        }

        void setDeleteSchemaVersion(boolean deleteSchemaVersion) {
            this.deleteSchemaVersion = deleteSchemaVersion;
        }

        boolean isPauseConnector() {
            return pauseConnector;
        }

        void setPauseConnector(boolean pauseConnector) {
            this.pauseConnector = pauseConnector;
        }

        boolean isResumeConnector() {
            return resumeConnector;
        }

        void setResumeConnector(boolean resumeConnector) {
            this.resumeConnector = resumeConnector;
        }

        boolean isRestartConnector() {
            return restartConnector;
        }

        void setRestartConnector(boolean restartConnector) {
            this.restartConnector = restartConnector;
        }

        boolean isRestartConnectorTask() {
            return restartConnectorTask;
        }

        void setRestartConnectorTask(boolean restartConnectorTask) {
            this.restartConnectorTask = restartConnectorTask;
        }

        boolean isUpdateConnectorConfig() {
            return updateConnectorConfig;
        }

        void setUpdateConnectorConfig(boolean updateConnectorConfig) {
            this.updateConnectorConfig = updateConnectorConfig;
        }

        boolean isDeleteConnector() {
            return deleteConnector;
        }

        void setDeleteConnector(boolean deleteConnector) {
            this.deleteConnector = deleteConnector;
        }
    }
}
