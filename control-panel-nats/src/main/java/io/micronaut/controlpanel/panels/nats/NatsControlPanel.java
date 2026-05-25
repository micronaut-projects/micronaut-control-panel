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
package io.micronaut.controlpanel.panels.nats;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.controlpanel.core.AbstractEachBeanControlPanel;
import io.micronaut.controlpanel.core.ControlPanel;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.nats.ConsumerRegistry;
import io.micronaut.nats.connect.NatsConnectionFactoryConfig;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Statistics;
import io.nats.client.Subscription;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamState;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Per-connection NATS diagnostics panel.
 */
@EachBean(Connection.class)
@Requires(classes = Connection.class)
@Requires(beans = Connection.class)
@Requires(property = NatsControlPanel.ENABLED_PROPERTY, notEquals = StringUtils.FALSE)
public final class NatsControlPanel extends AbstractEachBeanControlPanel<NatsControlPanel.Body> {

    public static final String NAME = "nats";
    public static final String ENABLED_PROPERTY = ControlPanelConfiguration.PREFIX + "." + NAME + ".enabled";
    public static final ControlPanel.Category CATEGORY = new ControlPanel.Category("nats", "NATS", "si si-natsdotio");

    private static final Pattern SECRET_TEXT = Pattern.compile("(?i)(password|passwd|pwd|token|credential|creds|secret|jwt|nkey)(\\s*[:=]\\s*)\\S+");

    private final String beanName;
    private final Connection connection;
    private final BeanContext beanContext;

    public NatsControlPanel(@Parameter String beanName,
                            Connection connection,
                            BeanContext beanContext,
                            @Named(NAME) ControlPanelConfiguration configuration) {
        super(NAME, configuration);
        this.beanName = beanName;
        this.connection = connection;
        this.beanContext = beanContext;
    }

    @Override
    protected String getBeanName() {
        return beanName;
    }

    @Override
    protected String getPanelName() {
        return NAME;
    }

    @Override
    public String getTitle() {
        return "NATS: " + beanName;
    }

    @Override
    public ControlPanel.Category getCategory() {
        return CATEGORY;
    }

    @Override
    public String getBadge() {
        return safeStatus().name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Body getBody() {
        return new Body(connectionInfo(), listeners(), jetStream());
    }

    private ConnectionInfo connectionInfo() {
        Connection.Status status = safeStatus();
        List<String> configuredServers = beanContext.findBean(NatsConnectionFactoryConfig.class, Qualifiers.byName(beanName))
            .flatMap(NatsConnectionFactoryConfig::getAddresses)
            .map(NatsControlPanel::redactAll)
            .orElse(List.of());
        List<String> knownServers = redactAll(connection.getServers());
        Statistics statistics = connection.getStatistics();
        return new ConnectionInfo(
            status.name(),
            status == Connection.Status.CONNECTED,
            redactUrl(connection.getConnectedUrl()),
            knownServers,
            configuredServers,
            redactText(connection.getLastError()),
            connection.getMaxPayload(),
            new ConnectionStatistics(
                statistics.getInMsgs(),
                statistics.getOutMsgs(),
                statistics.getInBytes(),
                statistics.getOutBytes(),
                statistics.getReconnects(),
                statistics.getDroppedCount(),
                statistics.getErrs(),
                statistics.getExceptions(),
                statistics.getOutstandingRequests()
            )
        );
    }

    private Connection.Status safeStatus() {
        try {
            return connection.getStatus();
        } catch (RuntimeException e) {
            return Connection.Status.DISCONNECTED;
        }
    }

    private ListenerSection listeners() {
        Optional<ConsumerRegistry> registry = beanContext.findBean(ConsumerRegistry.class);
        if (registry.isEmpty()) {
            return ListenerSection.unavailable("Micronaut NATS consumer registry is not available.");
        }
        try {
            List<ListenerInfo> listeners = registry.get().getConsumerIds().stream()
                .sorted()
                .map(id -> listenerInfo(registry.get(), id))
                .toList();
            return new ListenerSection(true, null, listeners);
        } catch (RuntimeException e) {
            return ListenerSection.unavailable(redactMessage(e));
        }
    }

    private static ListenerInfo listenerInfo(ConsumerRegistry registry, String id) {
        List<SubscriptionInfo> subscriptions = registry.getConsumerSubscription(id).stream()
            .sorted(Comparator.comparing(Subscription::getSubject, Comparator.nullsLast(String::compareTo))
                .thenComparing(Subscription::getQueueName, Comparator.nullsLast(String::compareTo)))
            .map(subscription -> new SubscriptionInfo(
                redactText(subscription.getSubject()),
                redactText(subscription.getQueueName())))
            .toList();
        return new ListenerInfo(redactRequired(id), subscriptions);
    }

    private JetStreamSection jetStream() {
        Optional<JetStreamManagement> management = beanContext.findBean(JetStreamManagement.class, Qualifiers.byName(beanName));
        if (management.isEmpty()) {
            return JetStreamSection.unavailable("JetStream is not configured for this connection.");
        }
        try {
            List<StreamSummary> streams = management.get().getStreams().stream()
                .sorted(Comparator.comparing(stream -> stream.getConfiguration().getName()))
                .map(stream -> streamSummary(management.get(), stream))
                .toList();
            return new JetStreamSection(true, null, streams);
        } catch (Exception e) {
            return JetStreamSection.unavailable(redactMessage(e));
        }
    }

    private static StreamSummary streamSummary(JetStreamManagement management, StreamInfo stream) {
        StreamConfiguration configuration = stream.getConfiguration();
        StreamState state = stream.getStreamState();
        List<ConsumerSummary> consumers = new ArrayList<>();
        String consumersError = null;
        try {
            consumers = management.getConsumers(configuration.getName()).stream()
                .sorted(Comparator.comparing(ConsumerInfo::getName))
                .map(NatsControlPanel::consumerSummary)
                .toList();
        } catch (Exception e) {
            consumersError = redactText(e.getMessage());
        }
        return new StreamSummary(
            redactRequired(configuration.getName()),
            redactAll(configuration.getSubjects()),
            configuration.getStorageType().name(),
            configuration.getRetentionPolicy().name(),
            state.getMsgCount(),
            state.getByteCount(),
            state.getConsumerCount(),
            consumers,
            consumersError
        );
    }

    private static ConsumerSummary consumerSummary(ConsumerInfo consumer) {
        ConsumerConfiguration configuration = consumer.getConsumerConfiguration();
        List<String> filterSubjects = configuration.getFilterSubjects() == null
            ? List.of()
            : redactAll(configuration.getFilterSubjects());
        return new ConsumerSummary(
            redactRequired(consumer.getName()),
            redactRequired(consumer.getStreamName()),
            redactText(configuration.getDurable()),
            redactText(configuration.getDeliverGroup()),
            filterSubjects,
            configuration.getAckPolicy().name(),
            consumer.getNumPending(),
            consumer.getNumAckPending(),
            consumer.getNumWaiting(),
            consumer.getRedelivered()
        );
    }

    static List<String> redactAll(Collection<String> values) {
        return values.stream()
            .map(NatsControlPanel::redactUrl)
            .sorted()
            .toList();
    }

    static @Nullable String redactUrl(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            URI uri = new URI(value);
            String userInfo = uri.getUserInfo() == null ? null : "***";
            URI sanitized = new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), null, uri.getFragment());
            return redactText(sanitized.toString());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return redactText(value.replaceAll("://[^/@]+@", "://***@"));
        }
    }

    static @Nullable String redactText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return SECRET_TEXT.matcher(value).replaceAll("$1$2[redacted]");
    }

    private static String redactRequired(String value) {
        return Optional.ofNullable(redactText(value)).orElse("");
    }

    private static String redactMessage(Exception exception) {
        return Optional.ofNullable(redactText(exception.getMessage())).orElse(exception.getClass().getSimpleName());
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record Body(ConnectionInfo connection, ListenerSection listeners, JetStreamSection jetStream) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record ConnectionInfo(String status,
                                 boolean connected,
                                 @Nullable String connectedUrl,
                                 List<String> knownServers,
                                 List<String> configuredServers,
                                 @Nullable String lastError,
                                 long maxPayload,
                                 ConnectionStatistics statistics) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record ConnectionStatistics(long inMsgs,
                                       long outMsgs,
                                       long inBytes,
                                       long outBytes,
                                       long reconnects,
                                       long dropped,
                                       long errors,
                                       long exceptions,
                                       long outstandingRequests) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record ListenerSection(boolean available, @Nullable String error, List<ListenerInfo> listeners) {
        static ListenerSection unavailable(String error) {
            return new ListenerSection(false, error, List.of());
        }
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record ListenerInfo(String id, List<SubscriptionInfo> subscriptions) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record SubscriptionInfo(@Nullable String subject, @Nullable String queue) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record JetStreamSection(boolean available, @Nullable String error, List<StreamSummary> streams) {
        static JetStreamSection unavailable(String error) {
            return new JetStreamSection(false, error, List.of());
        }
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record StreamSummary(String name,
                                List<String> subjects,
                                String storageType,
                                String retentionPolicy,
                                long messages,
                                long bytes,
                                long consumerCount,
                                List<ConsumerSummary> consumers,
                                @Nullable String consumersError) {
    }

    @ReflectiveAccess
    @SuppressWarnings("MissingJavadocType")
    public record ConsumerSummary(String name,
                                  String streamName,
                                  @Nullable String durable,
                                  @Nullable String deliverGroup,
                                  List<String> filterSubjects,
                                  String ackPolicy,
                                  long pending,
                                  long ackPending,
                                  long waiting,
                                  long redelivered) {
    }
}
