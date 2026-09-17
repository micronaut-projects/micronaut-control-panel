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
import java.util.regex.Pattern;

/**
 * Bounded in-memory collector for Pulsar failure events raised in this application process.
 */
@Internal
@Singleton
@Requires(classes = PulsarFailureEvent.class)
final class PulsarFailureEventCollector implements ApplicationEventListener<PulsarFailureEvent> {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern URI_USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)([^\\s/@]+@)");
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
        "-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final String SECRET_KEY_PATTERN = "token|authToken|authenticationToken|accessToken|refreshToken"
        + "|authParams|authData|password|passwd|pwd|secret|credential|credentials"
        + "|privateKey|privateKeyData|private-key|private-key-data|private_key|private_key_data"
        + "|tlsPrivateKey|tlsPrivateKeyData|tlsKeyFilePath|tlsPrivateKeyFilePath";
    private static final Pattern QUOTED_SECRET_VALUE = Pattern.compile(
        "(?i)\\b(" + SECRET_KEY_PATTERN + ")\\b(\\s*[:=]\\s*)([\"'])(.*?)(\\3)"
    );
    private static final Pattern UNQUOTED_SECRET_VALUE = Pattern.compile(
        "(?i)\\b(" + SECRET_KEY_PATTERN + ")\\b(\\s*[:=]\\s*)([^,;\\s\\}\\)]+)"
    );

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
            truncate(sanitize(nullSafe(event.getReason()))),
            truncate(sanitize(error))
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

    private static String sanitize(String value) {
        if (value.isBlank()) {
            return value;
        }
        String sanitized = PRIVATE_KEY_BLOCK.matcher(value).replaceAll(REDACTED);
        sanitized = URI_USER_INFO.matcher(sanitized).replaceAll("$1" + REDACTED + "@");
        sanitized = QUOTED_SECRET_VALUE.matcher(sanitized).replaceAll("$1$2$3" + REDACTED + "$5");
        return UNQUOTED_SECRET_VALUE.matcher(sanitized).replaceAll("$1$2" + REDACTED);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
