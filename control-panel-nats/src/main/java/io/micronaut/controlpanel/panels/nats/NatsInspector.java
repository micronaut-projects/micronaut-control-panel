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
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.nats.annotation.NatsConnection;
import io.micronaut.nats.annotation.NatsListener;
import io.micronaut.nats.annotation.Subject;
import io.micronaut.nats.connect.NatsConnectionFactoryConfig;
import io.nats.client.Connection;
import io.nats.client.Statistics;
import io.nats.client.api.ServerInfo;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Collects the NATS connections and subscriptions of the application.
 *
 * <p>Nothing is sent to the server: the connection status, server version and counters come from the client's memory,
 * and connections that nothing has used yet are not opened. Passwords, tokens and credential files are never shown,
 * and addresses are shown without user info.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = NatsConnectionFactoryConfig.class)
public class NatsInspector {

    static final String DEFAULT_CONNECTION = "default";

    private static final String CONNECTION_MEMBER = "connection";

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public NatsInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the connections, sorted by name
     */
    public List<NatsConnectionInfo> connections() {
        List<BeanRegistration<?>> open = beanContext.getActiveBeanRegistrations(Qualifiers.byType(Connection.class)).stream()
            .filter(registration -> registration.getBean() instanceof Connection)
            .toList();
        return beanContext.getBeansOfType(NatsConnectionFactoryConfig.class).stream()
            .map(config -> connection(config, open))
            .sorted(Comparator.comparing(NatsConnectionInfo::name))
            .toList();
    }

    private static NatsConnectionInfo connection(NatsConnectionFactoryConfig config, List<BeanRegistration<?>> open) {
        String name = config.getName();
        Optional<Connection> connection = open.stream()
            .filter(registration -> name.equals(registration.getIdentifier().getName()))
            .map(registration -> (Connection) registration.getBean())
            .findFirst();
        return new NatsConnectionInfo(
            name,
            config.getAddresses().orElse(List.of()).stream().map(NatsInspector::sanitize).toList(),
            authentication(config),
            hasTls(config),
            connection.map(c -> String.valueOf(c.getStatus())).orElse(null),
            connection.map(Connection::getConnectedUrl).map(NatsInspector::sanitize).orElse(null),
            connection.map(Connection::getServerInfo).map(NatsInspector::server).orElse(null),
            connection.map(Connection::getStatistics).map(NatsInspector::statistics).orElse(null)
        );
    }

    static String authentication(NatsConnectionFactoryConfig config) {
        if (config.getCredentials() != null) {
            return "credentials file";
        }
        if (config.getToken().isPresent()) {
            return "token";
        }
        if (config.getUsername().isPresent()) {
            return "user " + config.getUsername().get();
        }
        boolean userInAddress = config.getAddresses().orElse(List.of()).stream().anyMatch(address -> address.contains("@"));
        return userInAddress ? "user in address" : "none";
    }

    private static boolean hasTls(NatsConnectionFactoryConfig config) {
        return config.getAddresses().orElse(List.of()).stream().anyMatch(address -> address.startsWith("tls://"));
    }

    static @Nullable String server(@Nullable ServerInfo info) {
        if (info == null) {
            return null;
        }
        String cluster = info.getCluster();
        return info.getServerName() + " " + info.getVersion() + (StringUtils.isEmpty(cluster) ? "" : ", cluster " + cluster);
    }

    private static NatsConnectionInfo.Statistics statistics(Statistics statistics) {
        return new NatsConnectionInfo.Statistics(
            statistics.getInMsgs(),
            statistics.getOutMsgs(),
            statistics.getInBytes(),
            statistics.getOutBytes(),
            statistics.getReconnects(),
            statistics.getDroppedCount()
        );
    }

    /**
     * Removes the user info, query and fragment of an address, which can carry credentials.
     *
     * @param address the address
     * @return the address without user info, query or fragment
     */
    static String sanitize(String address) {
        try {
            URI uri = new URI(address);
            if (uri.getHost() == null) {
                return uri.getUserInfo() == null && uri.getQuery() == null && !address.contains("@") ? address : "(hidden)";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            return "(hidden)";
        }
    }

    /**
     * @return the {@code @Subject} methods, sorted by subject
     */
    public List<NatsSubscriptionInfo> subscriptions() {
        List<NatsSubscriptionInfo> subscriptions = new ArrayList<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(NatsListener.class))) {
            AnnotationValue<NatsListener> listener = definition.getAnnotation(NatsListener.class);
            String listenerConnection = listener == null ? "" : listener.stringValue(CONNECTION_MEMBER).orElse("");
            String listenerQueue = listener == null ? "" : listener.stringValue("queue").orElse("");
            for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                if (method.hasDeclaredAnnotation(Subject.class)) {
                    subscriptions.add(subscription(definition, method, listenerConnection, listenerQueue));
                }
            }
        }
        subscriptions.sort(Comparator.comparing(NatsSubscriptionInfo::subject).thenComparing(NatsSubscriptionInfo::listener));
        return subscriptions;
    }

    private static NatsSubscriptionInfo subscription(BeanDefinition<?> definition, ExecutableMethod<?, ?> method, String listenerConnection, String listenerQueue) {
        // @NatsConnection can also name the connection directly, which is how NatsConsumerAdvice resolves it
        String connection = firstNonBlank(method.stringValue(NatsConnection.class, CONNECTION_MEMBER).orElse(""),
            firstNonBlank(method.stringValue(Subject.class, CONNECTION_MEMBER).orElse(""), listenerConnection));
        String queue = firstNonBlank(method.stringValue(Subject.class, "queue").orElse(""), listenerQueue);
        return new NatsSubscriptionInfo(
            method.stringValue(Subject.class).orElse(""),
            queue.isEmpty() ? null : queue,
            definition.getBeanType().getName(),
            method.getMethodName(),
            connection.isEmpty() ? DEFAULT_CONNECTION : connection
        );
    }

    private static String firstNonBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }
}
