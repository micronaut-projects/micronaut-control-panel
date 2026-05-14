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
    }
}
