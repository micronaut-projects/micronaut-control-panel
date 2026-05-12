package example;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.messaging.Acknowledgement;
import io.micronaut.rabbitmq.annotation.Queue;
import io.micronaut.rabbitmq.annotation.RabbitListener;
import io.micronaut.rabbitmq.intercept.RabbitMQConsumerAdvice;
import jakarta.inject.Singleton;

@RabbitListener
@Requires(env = "rabbitmq")
class RabbitMqDemoListener {

    @Queue(value = "orders.created", numberOfConsumers = "2", prefetch = 25, reQueue = true)
    void receive(String body, Acknowledgement acknowledgement) {
        // Demo listener metadata only; the replacement processor prevents consumption.
    }

    @Queue(value = "audit.events", autoAcknowledgment = true)
    void audit(String body) {
        // Demo listener metadata only; the replacement processor prevents consumption.
    }

    @Singleton
    @Replaces(RabbitMQConsumerAdvice.class)
    static class NoopRabbitConsumerProcessor implements ExecutableMethodProcessor<Queue> {

        @Override
        public <T> void process(BeanDefinition<T> beanDefinition, ExecutableMethod<T, ?> method) {
            // Keep the example diagnostics-only by preventing RabbitMQ consumer startup.
        }
    }
}
