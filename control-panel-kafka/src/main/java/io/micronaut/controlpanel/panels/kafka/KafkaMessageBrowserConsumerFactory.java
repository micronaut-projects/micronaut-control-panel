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

import io.micronaut.configuration.kafka.config.KafkaDefaultConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import java.util.Properties;
import java.util.UUID;

/**
 * Creates isolated, non-committing consumers for read-only message browsing.
 */
@Singleton
@Internal
@Requires(property = KafkaClusterControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
final class KafkaMessageBrowserConsumerFactory {

    private static final String CLIENT_ID_PREFIX = "micronaut-control-panel-message-browser-";

    private final KafkaDefaultConfiguration kafkaDefaultConfiguration;

    KafkaMessageBrowserConsumerFactory(KafkaDefaultConfiguration kafkaDefaultConfiguration) {
        this.kafkaDefaultConfiguration = kafkaDefaultConfiguration;
    }

    Consumer<byte[], byte[]> createConsumer() {
        return new KafkaConsumer<>(browserConsumerConfig(kafkaDefaultConfiguration.getConfig()));
    }

    static Properties browserConsumerConfig(Properties defaultConfig) {
        Properties config = new Properties();
        config.putAll(defaultConfig);
        config.remove(ConsumerConfig.GROUP_ID_CONFIG);
        config.remove(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, CLIENT_ID_PREFIX + UUID.randomUUID());
        return config;
    }
}
