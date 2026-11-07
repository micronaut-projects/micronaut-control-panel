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
package io.micronaut.controlpanel.panels.rabbitmq;

import com.rabbitmq.client.Address;
import com.rabbitmq.client.Connection;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.connect.RabbitConnectionFactoryConfig;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects the RabbitMQ connections and consumers of the application.
 *
 * <p>Nothing is sent to the broker, so rendering the panel never waits on it. The connection state and broker version
 * come from the client's memory, and connections that nothing has used yet are not opened. Passwords are never read.
 * Messages are never read, published or acknowledged.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 2.3.0
 */
@Singleton
@Internal
@Requires(classes = RabbitConnectionFactoryConfig.class)
public class RabbitMqInspector {

    static final String DEFAULT_CONNECTION = "default";

    private final BeanContext beanContext;

    /**
     * @param beanContext the bean context
     */
    public RabbitMqInspector(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    /**
     * @return the connections, sorted by name
     */
    public List<RabbitMqConnectionInfo> connections() {
        List<BeanRegistration<?>> open = beanContext.getActiveBeanRegistrations(Qualifiers.byType(Connection.class)).stream()
            .filter(registration -> registration.getBean() instanceof Connection)
            .toList();
        return beanContext.getBeansOfType(RabbitConnectionFactoryConfig.class).stream()
            .map(config -> connection(config, open))
            .sorted(Comparator.comparing(RabbitMqConnectionInfo::name))
            .toList();
    }

    private static RabbitMqConnectionInfo connection(RabbitConnectionFactoryConfig config, List<BeanRegistration<?>> open) {
        String name = config.getName();
        List<String> addresses = config.getAddresses()
            .map(list -> list.stream().map(Address::toString).toList())
            .orElseGet(() -> List.of(config.getHost() + ":" + config.getPort()));
        Optional<Connection> connection = open.stream()
            // The default connection is only created when no named servers are configured, so it is the only one
            .filter(registration -> DEFAULT_CONNECTION.equals(name) || name.equals(registration.getIdentifier().getName()))
            .map(registration -> (Connection) registration.getBean())
            .findFirst();
        RabbitMqConnectionInfo.State state = connection
            .map(c -> c.isOpen() ? RabbitMqConnectionInfo.State.OPEN : RabbitMqConnectionInfo.State.CLOSED)
            .orElse(RabbitMqConnectionInfo.State.NOT_OPENED);
        return new RabbitMqConnectionInfo(
            name,
            addresses,
            config.getVirtualHost(),
            config.getUsername(),
            config.isSSL(),
            config.getConsumerExecutor(),
            config.getChannelPool() == null ? null : config.getChannelPool().getMaxIdleChannels().orElse(null),
            state,
            connection.map(RabbitMqInspector::broker).orElse(null)
        );
    }

    private static @Nullable String broker(Connection connection) {
        Map<String, Object> properties = connection.getServerProperties();
        if (properties == null || properties.get("product") == null) {
            return null;
        }
        Object version = properties.get("version");
        return properties.get("product") + (version == null ? "" : " " + version);
    }

    /**
     * @return the {@code @Queue} consumer methods, sorted by queue
     */
    public List<RabbitMqConsumerInfo> consumers() {
        List<RabbitMqConsumerInfo> consumers = new ArrayList<>();
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(RabbitListener.class))) {
            AnnotationValue<RabbitListener> listener = definition.getAnnotation(RabbitListener.class);
            String listenerConnection = listener == null ? "" : listener.stringValue("connection").orElse("");
            String listenerExecutor = listener == null ? "" : listener.stringValue("executor").orElse("");
            for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                if (!method.hasDeclaredAnnotation(Queue.class)) {
                    continue;
                }
                consumers.add(new RabbitMqConsumerInfo(
                    method.stringValue(Queue.class).orElse(""),
                    definition.getBeanType().getName(),
                    method.getMethodName(),
                    orDefault(method.stringValue(Queue.class, "connection").orElse(""), listenerConnection, DEFAULT_CONNECTION),
                    orDefault(method.stringValue(Queue.class, "executor").orElse(""), listenerExecutor, null),
                    method.stringValue(Queue.class, "numberOfConsumers").orElse("1"),
                    method.intValue(Queue.class, "prefetch").orElse(0),
                    method.booleanValue(Queue.class, "autoAcknowledgment").orElse(false),
                    method.booleanValue(Queue.class, "reQueue").orElse(false),
                    method.booleanValue(Queue.class, "exclusive").orElse(false)
                ));
            }
        }
        consumers.sort(Comparator.comparing(RabbitMqConsumerInfo::queue).thenComparing(RabbitMqConsumerInfo::listener));
        return consumers;
    }

    private static @Nullable String orDefault(String value, String fallback, @Nullable String defaultValue) {
        if (!value.isBlank()) {
            return value;
        }
        return fallback.isBlank() ? defaultValue : fallback;
    }
}
