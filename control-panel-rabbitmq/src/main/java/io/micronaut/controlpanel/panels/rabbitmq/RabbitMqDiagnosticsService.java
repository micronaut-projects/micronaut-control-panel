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
import io.micronaut.context.BeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqBody;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqConnectionInfo;
import io.micronaut.controlpanel.panels.rabbitmq.model.RabbitMqListenerInfo;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.messaging.Acknowledgement;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitConnection;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.connect.RabbitConnectionFactoryConfig;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * Builds the RabbitMQ diagnostics model rendered by the control panel.
 *
 * @author Micronaut Control Panel contributors
 * @since 2.0.0
 */
@Singleton
public class RabbitMqDiagnosticsService {

    private final BeanContext beanContext;
    private final Environment environment;
    private final Collection<RabbitConnectionFactoryConfig> connectionFactoryConfigs;
    private final RabbitMqControlPanelConfiguration configuration;
    private final RabbitMqHealthResolver healthResolver;
    private final RabbitMqMetricsResolver metricsResolver;

    /**
     * Constructor.
     *
     * @param beanContext bean context
     * @param environment environment
     * @param connectionFactoryConfigs RabbitMQ connection factory configs
     * @param configuration panel configuration
     * @param healthResolver health resolver
     * @param metricsResolver metrics resolver
     */
    public RabbitMqDiagnosticsService(BeanContext beanContext,
                                      Environment environment,
                                      Collection<RabbitConnectionFactoryConfig> connectionFactoryConfigs,
                                      RabbitMqControlPanelConfiguration configuration,
                                      RabbitMqHealthResolver healthResolver,
                                      RabbitMqMetricsResolver metricsResolver) {
        this.beanContext = beanContext;
        this.environment = environment;
        this.connectionFactoryConfigs = connectionFactoryConfigs;
        this.configuration = configuration;
        this.healthResolver = healthResolver;
        this.metricsResolver = metricsResolver;
    }

    /**
     * Builds the current RabbitMQ diagnostics body.
     *
     * @return current RabbitMQ diagnostics body
     */
    public RabbitMqBody getBody() {
        List<RabbitMqListenerInfo> listeners = findListeners();
        List<RabbitMqConnectionInfo> connections = findConnections();
        var metrics = metricsResolver.resolve();
        return new RabbitMqBody(
            listeners,
            connections,
            healthResolver.resolve(),
            metrics,
            configuration.getManagementUrl().orElse(null),
            listeners.size(),
            connections.size(),
            !metrics.meters().isEmpty()
        );
    }

    private List<RabbitMqListenerInfo> findListeners() {
        List<RabbitMqListenerInfo> listeners = new ArrayList<>();
        for (BeanDefinition<Object> definition : beanContext.getAllBeanDefinitions()) {
            if (!definition.hasAnnotation(RabbitListener.class)) {
                continue;
            }
            String listenerConnection = resolve(definition.stringValue(RabbitListener.class, "connection").orElse(EMPTY_STRING));
            String listenerExecutor = resolve(definition.stringValue(RabbitListener.class, "executor").orElse(EMPTY_STRING));
            for (ExecutableMethod<Object, ?> method : definition.getExecutableMethods()) {
                if (!method.hasAnnotation(Queue.class)) {
                    continue;
                }
                String connection = firstPresent(resolve(method.stringValue(Queue.class, "connection").orElse(EMPTY_STRING)), listenerConnection, RabbitConnection.DEFAULT_CONNECTION);
                String executor = firstPresent(resolve(method.stringValue(Queue.class, "executor").orElse(EMPTY_STRING)), listenerExecutor);
                String numberOfConsumers = resolve(method.stringValue(Queue.class, "numberOfConsumers").orElse("1"));
                listeners.add(new RabbitMqListenerInfo(
                    definition.getBeanType().getName(),
                    definition.getBeanType().getSimpleName(),
                    definition.getName(),
                    method.getDeclaringType().getName(),
                    method.getMethodName(),
                    method.getDescription(true),
                    resolve(method.stringValue(Queue.class, "value").orElse(EMPTY_STRING)),
                    connection,
                    executor,
                    display(numberOfConsumers),
                    method.intValue(Queue.class, "prefetch").orElse(0),
                    method.booleanValue(Queue.class, "exclusive").orElse(false),
                    method.booleanValue(Queue.class, "reQueue").orElse(false),
                    method.booleanValue(Queue.class, "autoAcknowledgment").orElse(false),
                    hasAcknowledgementArgument(method)
                ));
            }
        }
        listeners.sort(Comparator.comparing(RabbitMqListenerInfo::beanType).thenComparing(RabbitMqListenerInfo::method));
        return listeners;
    }

    private List<RabbitMqConnectionInfo> findConnections() {
        return connectionFactoryConfigs.stream()
            .map(config -> new RabbitMqConnectionInfo(
                config.getName(),
                RabbitMqRedactor.redact(config.getHost()),
                config.getPort(),
                RabbitMqRedactor.redact(config.getVirtualHost()),
                config.getAddresses()
                    .map(addresses -> addresses.stream().map(this::address).sorted().toList())
                    .orElse(List.of()),
                RabbitMqRedactor.redactCredential(config.getUsername()),
                config.getRequestedHeartbeat(),
                config.getConnectionTimeout(),
                config.getHandshakeTimeout(),
                config.isAutomaticRecoveryEnabled(),
                config.isTopologyRecoveryEnabled(),
                config.getNetworkRecoveryInterval(),
                format(config.getConfirmTimeout()),
                config.getRpc().getTimeout().map(this::format).orElse("not configured"),
                config.getChannelPool().getMaxIdleChannels().map(String::valueOf).orElse("unbounded")
            ))
            .sorted(Comparator.comparing(RabbitMqConnectionInfo::name))
            .toList();
    }

    private String address(Address address) {
        return RabbitMqRedactor.redact(address.getHost()) + ":" + address.getPort();
    }

    private String resolve(String value) {
        if (value.isBlank()) {
            return EMPTY_STRING;
        }
        try {
            return environment.getPlaceholderResolver()
                .resolvePlaceholders(value)
                .orElse(value);
        } catch (RuntimeException e) {
            return value + " (unresolved)";
        }
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return EMPTY_STRING;
    }

    private static String display(String value) {
        return value.isBlank() ? "not configured" : value;
    }

    private String format(Duration duration) {
        return duration.toString();
    }

    private static boolean hasAcknowledgementArgument(ExecutableMethod<Object, ?> method) {
        for (Argument<?> argument : method.getArguments()) {
            if (Acknowledgement.class.isAssignableFrom(argument.getType())) {
                return true;
            }
        }
        return false;
    }
}
