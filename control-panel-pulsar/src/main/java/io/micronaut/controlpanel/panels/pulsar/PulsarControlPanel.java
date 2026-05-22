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

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.type.Argument;
import io.micronaut.pulsar.PulsarConsumerRegistry;
import io.micronaut.pulsar.PulsarProducerRegistry;
import io.micronaut.pulsar.PulsarReaderRegistry;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Reader;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A control panel for registered Pulsar producers, consumers, readers, and in-process failure events.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.0.0
 */
@Singleton
@Requires(classes = PulsarConsumerRegistry.class)
public final class PulsarControlPanel extends AbstractControlPanel<PulsarControlPanel.Body> {

    public static final String NAME = "pulsar";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("messaging", "Messaging", "fas fa-paper-plane", 40);

    private static final Argument<PulsarConsumerRegistry> CONSUMER_REGISTRY = Argument.of(PulsarConsumerRegistry.class);
    private static final Argument<PulsarProducerRegistry> PRODUCER_REGISTRY = Argument.of(PulsarProducerRegistry.class);
    private static final Argument<PulsarReaderRegistry> READER_REGISTRY = Argument.of(PulsarReaderRegistry.class);
    private static final String UNKNOWN = "Unknown";

    private final BeanLocator beanLocator;
    private final PulsarControlPanelConfiguration pulsarConfiguration;
    private final PulsarFailureEventCollector failureEventCollector;

    public PulsarControlPanel(BeanLocator beanLocator,
                              @Named(NAME) ControlPanelConfiguration configuration,
                              PulsarControlPanelConfiguration pulsarConfiguration,
                              PulsarFailureEventCollector failureEventCollector) {
        super(NAME, configuration);
        this.beanLocator = beanLocator;
        this.pulsarConfiguration = pulsarConfiguration;
        this.failureEventCollector = failureEventCollector;
    }

    @Override
    public Body getBody() {
        Optional<PulsarProducerRegistry> producerRegistry = beanLocator.findBean(PRODUCER_REGISTRY);
        Optional<PulsarConsumerRegistry> consumerRegistry = beanLocator.findBean(CONSUMER_REGISTRY);
        Optional<PulsarReaderRegistry> readerRegistry = beanLocator.findBean(READER_REGISTRY);
        List<ProducerInfo> producers = producerRegistry
            .map(PulsarControlPanel::producers)
            .orElseGet(List::of);
        List<ConsumerInfo> consumers = consumerRegistry
            .map(PulsarControlPanel::consumers)
            .orElseGet(List::of);
        List<ReaderInfo> readers = readerRegistry
            .map(PulsarControlPanel::readers)
            .orElseGet(List::of);
        List<FailureInfo> failures = failureEventCollector.snapshot();
        int pausedConsumers = (int) consumers.stream().filter(ConsumerInfo::paused).count();
        Summary summary = new Summary(
            producers.size(),
            consumers.size(),
            readers.size(),
            pausedConsumers,
            failures.size()
        );
        return new Body(
            summary,
            producers,
            consumers,
            readers,
            failures,
            producerRegistry.isPresent(),
            consumerRegistry.isPresent(),
            readerRegistry.isPresent(),
            pulsarConfiguration.isAllowConsumerActions(),
            pulsarConfiguration.isIncludeFailureEvents(),
            false
        );
    }

    @Override
    public String getBadge() {
        Body body = getBody();
        return String.valueOf(body.summary().totalClients());
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    private static List<ProducerInfo> producers(PulsarProducerRegistry registry) {
        return registry.getProducerIds()
            .stream()
            .sorted()
            .map(id -> producerInfo(id, safe(() -> registry.getProducer(id)).orElse(null)))
            .toList();
    }

    private static ProducerInfo producerInfo(String id, @Nullable Producer<?> producer) {
        if (producer == null) {
            return new ProducerInfo(id, "", "", UNKNOWN, false, "");
        }
        return new ProducerInfo(
            id,
            safe(producer::getProducerName).orElse(""),
            safe(producer::getTopic).orElse(""),
            connectionState(safe(producer::isConnected).orElse(null)),
            true,
            safe(() -> String.valueOf(producer.getNumOfPartitions())).orElse("")
        );
    }

    private static List<ConsumerInfo> consumers(PulsarConsumerRegistry registry) {
        return registry.getConsumerIds()
            .stream()
            .sorted()
            .map(id -> consumerInfo(id, safe(() -> registry.getConsumer(id)).orElse(null), registry))
            .toList();
    }

    private static ConsumerInfo consumerInfo(String id, @Nullable Consumer<?> consumer, PulsarConsumerRegistry registry) {
        boolean paused = safe(() -> registry.isPaused(id)).orElse(false);
        if (consumer == null) {
            return new ConsumerInfo(id, "", "", "", UNKNOWN, paused, false, "");
        }
        return new ConsumerInfo(
            id,
            safe(consumer::getConsumerName).orElse(""),
            safe(consumer::getTopic).orElse(""),
            safe(consumer::getSubscription).orElse(""),
            paused ? "Paused" : "Running",
            paused,
            true,
            connectionState(safe(consumer::isConnected).orElse(null))
        );
    }

    private static List<ReaderInfo> readers(PulsarReaderRegistry registry) {
        Collection<Reader<?>> registeredReaders = registry.getReaders();
        if (registeredReaders == null || registeredReaders.isEmpty()) {
            return List.of();
        }
        List<Reader<?>> sortedReaders = registeredReaders.stream()
            .sorted(Comparator.comparing(reader -> safe(reader::getTopic).orElse("")))
            .toList();
        return java.util.stream.IntStream.range(0, sortedReaders.size())
            .mapToObj(index -> readerInfo(index + 1, sortedReaders.get(index)))
            .toList();
    }

    private static ReaderInfo readerInfo(int index, Reader<?> reader) {
        return new ReaderInfo(
            "reader-" + index,
            safe(reader::getTopic).orElse(""),
            connectionState(safe(reader::isConnected).orElse(null)),
            safe(reader::hasReachedEndOfTopic).orElse(false)
        );
    }

    private static String connectionState(@Nullable Boolean connected) {
        if (connected == null) {
            return UNKNOWN;
        }
        return connected ? "Connected" : "Disconnected";
    }

    private static <T> Optional<T> safe(Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Body for the Pulsar panel rendering.
     *
     * @param summary summary counts
     * @param producers registered producers
     * @param consumers registered consumers
     * @param readers registered readers
     * @param failures recent failure events
     * @param producerRegistryPresent whether the producer registry bean exists
     * @param consumerRegistryPresent whether the consumer registry bean exists
     * @param readerRegistryPresent whether the reader registry bean exists
     * @param consumerActionsAllowed whether pause/resume actions are enabled
     * @param failureEventsIncluded whether failure events are included
     * @param adminEnrichmentAvailable whether broker/admin enrichment is available
     */
    @ReflectiveAccess
    public record Body(
        Summary summary,
        List<ProducerInfo> producers,
        List<ConsumerInfo> consumers,
        List<ReaderInfo> readers,
        List<FailureInfo> failures,
        boolean producerRegistryPresent,
        boolean consumerRegistryPresent,
        boolean readerRegistryPresent,
        boolean consumerActionsAllowed,
        boolean failureEventsIncluded,
        boolean adminEnrichmentAvailable
    ) {
    }

    @ReflectiveAccess
    public record Summary(int producers, int consumers, int readers, int pausedConsumers, int failures) {
        public int totalClients() {
            return producers + consumers + readers;
        }
    }

    @ReflectiveAccess
    public record ProducerInfo(String id, String name, String topic, String state, boolean present, String partitions) {
    }

    @ReflectiveAccess
    public record ConsumerInfo(String id, String name, String topic, String subscription, String state, boolean paused, boolean present, String connectionState) {
    }

    @ReflectiveAccess
    public record ReaderInfo(String id, String topic, String state, boolean endOfTopic) {
    }

    @ReflectiveAccess
    public record FailureInfo(String timestamp, String clientType, String clientName, String reason, String error) {
    }
}
