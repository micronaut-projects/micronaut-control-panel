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
package io.micronaut.controlpanel.panels.pulsar;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.pulsar.events.ConsumerSubscriptionFailedEvent;
import io.micronaut.pulsar.events.ProducerSubscriptionFailedEvent;
import io.micronaut.pulsar.events.PulsarFailureEvent;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Bounded in-memory collector for Pulsar failure events raised in this application process.
 */
@Internal
@Singleton
@Requires(classes = PulsarFailureEvent.class)
final class PulsarFailureEventCollector implements ApplicationEventListener<PulsarFailureEvent> {

    private static final int MAX_REASON_LENGTH = 500;

    private final PulsarControlPanelConfiguration configuration;
    private final Deque<PulsarControlPanel.FailureInfo> failures = new ArrayDeque<>();

    PulsarFailureEventCollector(PulsarControlPanelConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void onApplicationEvent(PulsarFailureEvent event) {
        if (!configuration.isIncludeFailureEvents()) {
            return;
        }
        synchronized (failures) {
            failures.addLast(toFailureInfo(event));
            int max = Math.max(0, configuration.getMaxFailureEvents());
            while (failures.size() > max) {
                failures.removeFirst();
            }
        }
    }

    List<PulsarControlPanel.FailureInfo> snapshot() {
        if (!configuration.isIncludeFailureEvents()) {
            return List.of();
        }
        synchronized (failures) {
            return new ArrayList<>(failures)
                .stream()
                .sorted(Comparator.comparing(PulsarControlPanel.FailureInfo::timestamp).reversed())
                .toList();
        }
    }

    private static PulsarControlPanel.FailureInfo toFailureInfo(PulsarFailureEvent event) {
        String clientType = event.getClass().getSimpleName();
        String clientName = "";
        String error = "";
        if (event instanceof ConsumerSubscriptionFailedEvent consumerFailed) {
            clientType = "Consumer";
            clientName = nullSafe(consumerFailed.getConsumerName());
            error = consumerFailed.getSourceError()
                .map(PulsarFailureEventCollector::describeError)
                .orElse("");
        } else if (event instanceof ProducerSubscriptionFailedEvent producerFailed) {
            clientType = "Producer";
            clientName = nullSafe(producerFailed.getProducerName());
            error = Optional.ofNullable(producerFailed.getSourceError())
                .map(PulsarFailureEventCollector::describeError)
                .orElse("");
        }
        return new PulsarControlPanel.FailureInfo(
            Instant.now().toString(),
            clientType,
            clientName,
            truncate(nullSafe(event.getReason())),
            truncate(error)
        );
    }

    private static String describeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REASON_LENGTH) + "...";
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
