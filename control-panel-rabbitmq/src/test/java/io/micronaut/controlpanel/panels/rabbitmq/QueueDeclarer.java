package io.micronaut.controlpanel.panels.rabbitmq;

import com.rabbitmq.client.Channel;
import io.micronaut.context.annotation.Requires;
import io.micronaut.rabbitmq.connect.ChannelInitializer;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Declares the queues the test listeners consume from.
 */
@Singleton
@Requires(property = "spec.name", value = "RabbitMqControlPanelTest")
class QueueDeclarer extends ChannelInitializer {

    @Override
    public void initialize(Channel channel, String name) throws IOException {
        channel.queueDeclare("orders", true, false, false, null);
        channel.queueDeclare("audit", true, false, false, null);
    }
}
