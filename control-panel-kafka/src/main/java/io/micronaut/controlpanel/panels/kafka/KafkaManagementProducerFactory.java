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
import jakarta.inject.Singleton;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.Properties;
import java.util.UUID;

/**
 * Creates isolated producers for Kafka control-panel test-message writes.
 */
@Singleton
@Internal
@Requires(beans = KafkaDefaultConfiguration.class)
final class KafkaManagementProducerFactory {

    private final KafkaDefaultConfiguration kafkaDefaultConfiguration;

    KafkaManagementProducerFactory(KafkaDefaultConfiguration kafkaDefaultConfiguration) {
        this.kafkaDefaultConfiguration = kafkaDefaultConfiguration;
    }

    Producer<byte[], byte[]> createProducer() {
        return new KafkaProducer<>(producerConfig(kafkaDefaultConfiguration.getConfig()));
    }

    static Properties producerConfig(Properties defaults) {
        Properties config = new Properties();
        config.putAll(defaults);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "micronaut-control-panel-kafka-producer-" + UUID.randomUUID());
        config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        return config;
    }
}
