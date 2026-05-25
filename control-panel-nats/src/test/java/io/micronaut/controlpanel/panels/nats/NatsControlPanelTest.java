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
import io.micronaut.context.Qualifier;
import io.micronaut.controlpanel.core.config.ControlPanelConfiguration;
import io.micronaut.nats.ConsumerRegistry;
import io.micronaut.nats.connect.NatsConnectionFactoryConfig;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Statistics;
import io.nats.client.Subscription;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class NatsControlPanelTest {

    @Test
    void rendersConnectionListenersAndJetStreamSummaries() throws Exception {
        BeanContext beanContext = mock(BeanContext.class);
        Connection connection = connection(Connection.Status.CONNECTED);
        NatsConnectionFactoryConfig natsConfig = new NatsConnectionFactoryConfig("default");
        natsConfig.setAddresses(List.of("nats://user:secret@localhost:4222", "nats://backup:4222"));
        ConsumerRegistry registry = consumerRegistry();
        JetStreamManagement management = jetStreamManagement();
        when(beanContext.findBean(eq(NatsConnectionFactoryConfig.class), any(Qualifier.class))).thenReturn(Optional.of(natsConfig));
        when(beanContext.findBean(ConsumerRegistry.class)).thenReturn(Optional.of(registry));
        when(beanContext.findBean(eq(JetStreamManagement.class), any(Qualifier.class))).thenReturn(Optional.of(management));

        NatsControlPanel.Body body = panel(beanContext, connection).getBody();

        assertTrue(body.connection().connected());
        assertEquals("CONNECTED", body.connection().status());
        assertEquals("nats://***@localhost:4222", body.connection().connectedUrl());
        assertEquals(List.of("nats://***@localhost:4222", "nats://backup:4222"), body.connection().configuredServers());
        assertEquals(7, body.connection().statistics().inMsgs());
        assertEquals(1, body.listeners().listeners().size());
        assertEquals("orders-listener", body.listeners().listeners().getFirst().id());
        assertEquals("orders.created", body.listeners().listeners().getFirst().subscriptions().getFirst().subject());
        assertEquals("orders-workers", body.listeners().listeners().getFirst().subscriptions().getFirst().queue());
        assertTrue(body.jetStream().available());
        assertEquals(1, body.jetStream().streams().size());
        NatsControlPanel.StreamSummary stream = body.jetStream().streams().getFirst();
        assertEquals("ORDERS", stream.name());
        assertEquals(List.of("orders.*"), stream.subjects());
        assertEquals(12, stream.messages());
        assertEquals(1, stream.consumers().size());
        assertEquals("ORDERS_DURABLE", stream.consumers().getFirst().durable());
        assertEquals(3, stream.consumers().getFirst().pending());
    }

    @Test
    void rendersUnavailableJetStreamWhenBeanIsMissing() {
        BeanContext beanContext = mock(BeanContext.class);
        when(beanContext.findBean(eq(NatsConnectionFactoryConfig.class), any(Qualifier.class))).thenReturn(Optional.empty());
        when(beanContext.findBean(ConsumerRegistry.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(eq(JetStreamManagement.class), any(Qualifier.class))).thenReturn(Optional.empty());

        NatsControlPanel.Body body = panel(beanContext, connection(Connection.Status.DISCONNECTED)).getBody();

        assertFalse(body.connection().connected());
        assertFalse(body.jetStream().available());
        assertEquals("JetStream is not configured for this connection.", body.jetStream().error());
        assertFalse(body.listeners().available());
    }

    @Test
    void handlesJetStreamExceptionsWithoutThrowing() throws Exception {
        BeanContext beanContext = mock(BeanContext.class);
        JetStreamManagement management = mock(JetStreamManagement.class);
        when(management.getStreams()).thenThrow(new IOException("token=secret failed"));
        when(beanContext.findBean(eq(NatsConnectionFactoryConfig.class), any(Qualifier.class))).thenReturn(Optional.empty());
        when(beanContext.findBean(ConsumerRegistry.class)).thenReturn(Optional.empty());
        when(beanContext.findBean(eq(JetStreamManagement.class), any(Qualifier.class))).thenReturn(Optional.of(management));

        NatsControlPanel.Body body = panel(beanContext, connection(Connection.Status.CONNECTED)).getBody();

        assertFalse(body.jetStream().available());
        assertEquals("token=[redacted] failed", body.jetStream().error());
    }

    @Test
    void redactsCredentialBearingUrlsAndText() {
        assertEquals("nats://***@localhost:4222", NatsControlPanel.redactUrl("nats://user:password@localhost:4222?token=secret"));
        assertEquals("credential=[redacted] failed", NatsControlPanel.redactText("credential=/home/me/.nats/creds failed"));
        assertNull(NatsControlPanel.redactText(null));
    }

    private static NatsControlPanel panel(BeanContext beanContext, Connection connection) {
        return new NatsControlPanel("default", connection, beanContext, mock(ControlPanelConfiguration.class));
    }

    private static Connection connection(Connection.Status status) {
        Connection connection = mock(Connection.class);
        when(connection.getStatus()).thenReturn(status);
        when(connection.getServers()).thenReturn(List.of("nats://user:secret@localhost:4222", "nats://backup:4222"));
        when(connection.getConnectedUrl()).thenReturn("nats://user:secret@localhost:4222");
        when(connection.getLastError()).thenReturn(null);
        when(connection.getMaxPayload()).thenReturn(1048576L);
        Statistics statistics = mock(Statistics.class);
        when(statistics.getInMsgs()).thenReturn(7L);
        when(statistics.getOutMsgs()).thenReturn(5L);
        when(statistics.getInBytes()).thenReturn(700L);
        when(statistics.getOutBytes()).thenReturn(500L);
        when(statistics.getReconnects()).thenReturn(1L);
        when(statistics.getDroppedCount()).thenReturn(2L);
        when(statistics.getErrs()).thenReturn(3L);
        when(statistics.getExceptions()).thenReturn(4L);
        when(statistics.getOutstandingRequests()).thenReturn(6L);
        when(connection.getStatistics()).thenReturn(statistics);
        return connection;
    }

    private static ConsumerRegistry consumerRegistry() {
        ConsumerRegistry registry = mock(ConsumerRegistry.class);
        Subscription subscription = mock(Subscription.class);
        when(subscription.getSubject()).thenReturn("orders.created");
        when(subscription.getQueueName()).thenReturn("orders-workers");
        when(registry.getConsumerIds()).thenReturn(Set.of("orders-listener"));
        when(registry.getConsumerSubscription("orders-listener")).thenReturn(Set.of(subscription));
        return registry;
    }

    private static JetStreamManagement jetStreamManagement() throws Exception {
        JetStreamManagement management = mock(JetStreamManagement.class);
        StreamConfiguration streamConfiguration = mock(StreamConfiguration.class);
        when(streamConfiguration.getName()).thenReturn("ORDERS");
        when(streamConfiguration.getSubjects()).thenReturn(List.of("orders.*"));
        when(streamConfiguration.getStorageType()).thenReturn(StorageType.File);
        when(streamConfiguration.getRetentionPolicy()).thenReturn(RetentionPolicy.Limits);
        StreamState state = mock(StreamState.class);
        when(state.getMsgCount()).thenReturn(12L);
        when(state.getByteCount()).thenReturn(1024L);
        when(state.getConsumerCount()).thenReturn(1L);
        StreamInfo stream = mock(StreamInfo.class);
        when(stream.getConfiguration()).thenReturn(streamConfiguration);
        when(stream.getStreamState()).thenReturn(state);
        when(management.getStreams()).thenReturn(List.of(stream));

        ConsumerConfiguration consumerConfiguration = mock(ConsumerConfiguration.class);
        when(consumerConfiguration.getDurable()).thenReturn("ORDERS_DURABLE");
        when(consumerConfiguration.getDeliverGroup()).thenReturn("orders-deliver");
        when(consumerConfiguration.getFilterSubjects()).thenReturn(List.of("orders.created"));
        when(consumerConfiguration.getAckPolicy()).thenReturn(AckPolicy.Explicit);
        ConsumerInfo consumer = mock(ConsumerInfo.class);
        when(consumer.getName()).thenReturn("orders-consumer");
        when(consumer.getStreamName()).thenReturn("ORDERS");
        when(consumer.getConsumerConfiguration()).thenReturn(consumerConfiguration);
        when(consumer.getNumPending()).thenReturn(3L);
        when(consumer.getNumAckPending()).thenReturn(2L);
        when(consumer.getNumWaiting()).thenReturn(1L);
        when(consumer.getRedelivered()).thenReturn(0L);
        when(management.getConsumers("ORDERS")).thenReturn(List.of(consumer));
        return management;
    }
}
