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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import io.micronaut.pulsar.PulsarProducerRegistry;
import io.micronaut.pulsar.PulsarReaderRegistry;
import io.micronaut.pulsar.config.PulsarClientConfiguration;
import jakarta.inject.Singleton;
import org.apache.pulsar.client.api.Authentication;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerStats;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.ProducerStats;
import org.apache.pulsar.client.api.Reader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Collects the Pulsar client configuration and the consumers, producers and readers of the application.
 *
 * <p>Nothing is sent to the broker: the connection state and counters come from the client's memory, and registries
 * that nothing has used yet are not created. Tokens, OAuth credentials and TLS passwords are never shown, and URLs are
 * shown without user info or query strings.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = PulsarConsumerRegistry.class)
public class PulsarInspector {

    private static final Logger LOG = LoggerFactory.getLogger(PulsarInspector.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public PulsarInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the client configuration, or {@code null} if Pulsar is not configured
     */
    public @Nullable PulsarClientInfo client() {
        Optional<PulsarClientConfiguration> found;
        try {
            found = beanContext.findBean(PulsarClientConfiguration.class);
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Pulsar client configuration", e);
            return null;
        }
        if (found.isEmpty()) {
            return null;
        }
        PulsarClientConfiguration configuration = found.get();
        String serviceUrl = configuration.getServiceUrl();
        boolean dynamic = serviceUrl == null && configuration.getServiceUrlProvider().isPresent();
        return new PulsarClientInfo(
            dynamic ? "(service URL provider)" : sanitize(String.valueOf(serviceUrl)),
            authentication(configuration),
            serviceUrl != null && (serviceUrl.startsWith("pulsar+ssl://") || serviceUrl.startsWith("https://")),
            configuration.getDefaultTenant().orElse(null),
            configuration.getProxyUrl().map(PulsarInspector::sanitize).orElse(null)
        );
    }

    private static String authentication(PulsarClientConfiguration configuration) {
        Authentication authentication;
        try {
            authentication = configuration.getAuthentication();
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Pulsar authentication", e);
            return "unknown";
        }
        if (authentication == null) {
            return "none";
        }
        return authenticationLabel(authentication.getClass().getSimpleName());
    }

    /**
     * @param type the simple name of the authentication class
     * @return the label of the authentication method
     */
    static String authenticationLabel(String type) {
        return switch (type) {
            case "AuthenticationDisabled" -> "none";
            case "AuthenticationToken" -> "token";
            case "AuthenticationOAuth2" -> "OAuth 2.0";
            case "AuthenticationTls", "AuthenticationKeyStoreTls" -> "TLS client certificate";
            case "AuthenticationBasic" -> "basic";
            case "AuthenticationSasl" -> "SASL";
            case "AuthenticationAthenz" -> "Athenz";
            default -> type;
        };
    }

    /**
     * @return the consumers, sorted by topic
     */
    public List<PulsarConsumerInfo> consumers() {
        return active(PulsarConsumerRegistry.class)
            .map(PulsarInspector::consumers)
            .orElse(List.of());
    }

    private static List<PulsarConsumerInfo> consumers(PulsarConsumerRegistry registry) {
        List<PulsarConsumerInfo> consumers = new ArrayList<>();
        for (Map.Entry<String, Consumer<?>> entry : registry.getConsumers().entrySet()) {
            Consumer<?> consumer = entry.getValue();
            try {
                ConsumerStats stats = consumer.getStats();
                consumers.add(new PulsarConsumerInfo(
                    entry.getKey(),
                    String.valueOf(consumer.getTopic()),
                    String.valueOf(consumer.getSubscription()),
                    String.valueOf(consumer.getConsumerName()),
                    consumer.isConnected(),
                    paused(registry, entry.getKey()),
                    stats == null ? 0 : stats.getTotalMsgsReceived(),
                    stats == null ? 0 : stats.getTotalAcksSent(),
                    stats == null ? 0 : stats.getTotalReceivedFailed()
                ));
            } catch (RuntimeException e) {
                LOG.debug("Unable to read the Pulsar consumer '{}'", entry.getKey(), e);
            }
        }
        consumers.sort(Comparator.comparing(PulsarConsumerInfo::topic).thenComparing(PulsarConsumerInfo::id));
        return consumers;
    }

    private static boolean paused(PulsarConsumerRegistry registry, String id) {
        try {
            return registry.isPaused(id);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @return the producers, sorted by topic
     */
    public List<PulsarProducerInfo> producers() {
        return active(PulsarProducerRegistry.class)
            .map(PulsarInspector::producers)
            .orElse(List.of());
    }

    private static List<PulsarProducerInfo> producers(PulsarProducerRegistry registry) {
        List<PulsarProducerInfo> producers = new ArrayList<>();
        for (Map.Entry<String, Producer<?>> entry : registry.getProducers().entrySet()) {
            Producer<?> producer = entry.getValue();
            try {
                ProducerStats stats = producer.getStats();
                producers.add(new PulsarProducerInfo(
                    entry.getKey(),
                    String.valueOf(producer.getTopic()),
                    String.valueOf(producer.getProducerName()),
                    producer.isConnected(),
                    stats == null ? 0 : stats.getTotalMsgsSent(),
                    stats == null ? 0 : stats.getTotalSendFailed()
                ));
            } catch (RuntimeException e) {
                LOG.debug("Unable to read the Pulsar producer '{}'", entry.getKey(), e);
            }
        }
        producers.sort(Comparator.comparing(PulsarProducerInfo::topic).thenComparing(PulsarProducerInfo::id));
        return producers;
    }

    /**
     * @return the readers, sorted by topic
     */
    public List<PulsarReaderInfo> readers() {
        return active(PulsarReaderRegistry.class)
            .map(registry -> registry.getReaders().stream()
                .map(PulsarInspector::reader)
                .sorted(Comparator.comparing(PulsarReaderInfo::topic))
                .toList())
            .orElse(List.of());
    }

    private static PulsarReaderInfo reader(Reader<?> reader) {
        return new PulsarReaderInfo(String.valueOf(reader.getTopic()), reader.isConnected());
    }

    private <T> Optional<T> active(Class<T> type) {
        try {
            return beanContext.getActiveBeanRegistrations(Qualifiers.byType(type)).stream()
                .map(BeanRegistration::getBean)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
        } catch (RuntimeException e) {
            LOG.debug("Unable to read the Pulsar {}", type.getSimpleName(), e);
            return Optional.empty();
        }
    }

    /**
     * Removes the user info, query and fragment of each URL of a Pulsar service URL, which can carry credentials.
     * Service URLs can list several hosts, such as {@code pulsar://broker-1:6650,broker-2:6650}.
     *
     * @param serviceUrl the service URL
     * @return the service URL without user info, query or fragment
     */
    static String sanitize(String serviceUrl) {
        int schemeEnd = serviceUrl.indexOf("://");
        if (schemeEnd < 0) {
            return serviceUrl.contains("@") || serviceUrl.contains("?") ? "(hidden)" : serviceUrl;
        }
        String scheme = serviceUrl.substring(0, schemeEnd);
        String rest = serviceUrl.substring(schemeEnd + 3);
        int pathStart = indexOfAny(rest, '/', '?', '#');
        String hosts = pathStart < 0 ? rest : rest.substring(0, pathStart);
        String tail = pathStart < 0 ? "" : rest.substring(pathStart);
        String sanitizedHosts = Arrays.stream(hosts.split(","))
            .map(host -> host.substring(host.lastIndexOf('@') + 1))
            .collect(Collectors.joining(","));
        try {
            String path = tail.isEmpty() ? "" : new URI("http://h" + tail).getPath();
            return scheme + "://" + sanitizedHosts + (path == null ? "" : path);
        } catch (URISyntaxException e) {
            return scheme + "://" + sanitizedHosts;
        }
    }

    private static int indexOfAny(String value, char... chars) {
        int result = -1;
        for (char c : chars) {
            int index = value.indexOf(c);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }
}
