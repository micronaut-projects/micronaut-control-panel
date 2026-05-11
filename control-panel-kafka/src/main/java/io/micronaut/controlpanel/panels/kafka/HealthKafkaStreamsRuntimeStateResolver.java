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

import io.micronaut.configuration.kafka.streams.ConfiguredStreamBuilder;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.apache.kafka.streams.StreamsConfig;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Resolves Kafka Streams runtime state from the visible Micronaut Health endpoint result.
 */
@Internal
@Singleton
@Requires(classes = HealthEndpoint.class)
@Requires(beans = HealthEndpoint.class)
final class HealthKafkaStreamsRuntimeStateResolver implements KafkaStreamsRuntimeStateResolver {

    static final String UNAVAILABLE_MESSAGE = "Runtime state is unavailable because Kafka Streams health details are hidden, disabled, or not present.";

    private static final String KAFKA_STREAMS = "kafkaStreams";

    private final HealthEndpoint healthEndpoint;

    HealthKafkaStreamsRuntimeStateResolver(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    public KafkaStreamsRuntimeState resolve(String beanName, ConfiguredStreamBuilder builder) {
        HealthResult health = readVisibleHealth();
        if (health == null) {
            return KafkaStreamsRuntimeState.unavailable(UNAVAILABLE_MESSAGE);
        }
        return resolve(beanName, builder, health);
    }

    static KafkaStreamsRuntimeState resolve(String beanName, ConfiguredStreamBuilder builder, HealthResult health) {
        HealthResult kafkaStreams = kafkaStreamsHealth(health);
        if (kafkaStreams == null) {
            return KafkaStreamsRuntimeState.unavailable(UNAVAILABLE_MESSAGE);
        }
        HealthResult application = applicationHealth(kafkaStreams, candidates(beanName, builder));
        if (application == null) {
            return KafkaStreamsRuntimeState.unavailable(UNAVAILABLE_MESSAGE);
        }
        return stateFromApplicationHealth(application);
    }

    private HealthResult readVisibleHealth() {
        Publisher<HealthResult> health = healthEndpoint.getHealth(null);
        return Mono.from(health).block();
    }

    private static HealthResult kafkaStreamsHealth(HealthResult health) {
        if (KAFKA_STREAMS.equals(normalizeKey(health.getName()))) {
            return health;
        }
        Object details = health.getDetails();
        if (details instanceof Map<?, ?> detailsMap) {
            Object value = findValue(detailsMap, KAFKA_STREAMS);
            if (value instanceof HealthResult result) {
                return result;
            }
        }
        return null;
    }

    private static HealthResult applicationHealth(HealthResult kafkaStreams, Set<String> candidates) {
        Object details = kafkaStreams.getDetails();
        if (!(details instanceof Map<?, ?> applications)) {
            return null;
        }
        for (String candidate : candidates) {
            Object value = findValue(applications, candidate);
            if (value instanceof HealthResult result) {
                return result;
            }
        }
        return null;
    }

    private static KafkaStreamsRuntimeState stateFromApplicationHealth(HealthResult application) {
        String status = statusName(application.getStatus());
        Object details = application.getDetails();
        if (!(details instanceof Map<?, ?> detailsMap)) {
            return KafkaStreamsRuntimeState.available(status, null, List.of());
        }
        String message = stringValue(detailsMap.get("error"));
        List<KafkaStreamsRuntimeState.ThreadState> threads = new ArrayList<>();
        for (Object value : detailsMap.values()) {
            if (value instanceof Map<?, ?> threadMap && threadMap.containsKey("threadName")) {
                threads.add(threadState(threadMap));
            }
        }
        return KafkaStreamsRuntimeState.available(status, message, threads);
    }

    private static KafkaStreamsRuntimeState.ThreadState threadState(Map<?, ?> threadMap) {
        return new KafkaStreamsRuntimeState.ThreadState(
                stringValue(threadMap.get("threadName")),
                stringValue(threadMap.get("threadState")),
                stringValue(threadMap.get("adminClientId")),
                stringValue(threadMap.get("consumerClientId")),
                stringValue(threadMap.get("restoreConsumerClientId")),
                stringList(threadMap.get("producerClientIds")),
                false,
                taskSummary(threadMap.get("activeTasks")),
                taskSummary(threadMap.get("standbyTasks"))
        );
    }

    private static KafkaStreamsRuntimeState.TaskSummary taskSummary(Object value) {
        if (!(value instanceof Map<?, ?> taskMap)) {
            return KafkaStreamsRuntimeState.TaskSummary.empty();
        }
        return new KafkaStreamsRuntimeState.TaskSummary(
                stringValue(taskMap.get("taskId")),
                stringList(taskMap.get("partitions")),
                false,
                0
        );
    }

    private static Set<String> candidates(String beanName, ConfiguredStreamBuilder builder) {
        Properties properties = builder.getConfiguration();
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(properties.getProperty(StreamsConfig.APPLICATION_ID_CONFIG));
        candidates.add(properties.getProperty(StreamsConfig.CLIENT_ID_CONFIG));
        candidates.add(builder.getName());
        candidates.add(beanName);
        candidates.removeIf(value -> value == null || value.isBlank());
        return candidates;
    }

    private static Object findValue(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (key.equals(normalizeKey(stringValue(entry.getKey())))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return StringUtils.trimToNull(key.endsWith("()") ? key.substring(0, key.length() - 2) : key);
    }

    private static String statusName(HealthStatus status) {
        return status == null ? null : status.getName();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(HealthKafkaStreamsRuntimeStateResolver::stringValue)
                    .filter(Objects::nonNull)
                    .toList();
        }
        String single = stringValue(value);
        return single == null ? List.of() : List.of(single);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
