package com.team27.amazon.contracts.rabbit;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for §2.7 consumer queue + DLQ declaration.
 */
public final class RabbitConsumerTopology {

    private RabbitConsumerTopology() {}

    public record ConsumerQueues(Queue queue, Queue dlq, Binding binding, Binding dlqBinding) {}

    public static TopicExchange topicExchange(String name) {
        return new TopicExchange(name, true, false);
    }

    public static TopicExchange deadLetterExchange(String sourceExchange) {
        return new TopicExchange(EventExchanges.deadLetterExchange(sourceExchange), true, false);
    }

    public static Queue consumerQueue(String queueName, String sourceExchange) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", EventExchanges.deadLetterExchange(sourceExchange))
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey(queueName))
                .build();
    }

    public static Queue deadLetterQueue(String queueName) {
        return QueueBuilder.durable(deadLetterRoutingKey(queueName)).build();
    }

    public static String deadLetterRoutingKey(String queueName) {
        return queueName + ".dlq";
    }

    public static Binding bindQueue(Queue queue, TopicExchange exchange, String routingKey) {
        return org.springframework.amqp.core.BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    public static Binding bindDeadLetterQueue(Queue dlq, TopicExchange dlx, String queueName) {
        return org.springframework.amqp.core.BindingBuilder.bind(dlq)
                .to(dlx)
                .with(deadLetterRoutingKey(queueName));
    }

    public static ConsumerQueues consumer(
            String queueName,
            String sourceExchangeName,
            TopicExchange sourceExchange,
            TopicExchange deadLetterExchange,
            String routingKey) {
        Queue queue = consumerQueue(queueName, sourceExchangeName);
        Queue dlq = deadLetterQueue(queueName);
        return new ConsumerQueues(
                queue,
                dlq,
                bindQueue(queue, sourceExchange, routingKey),
                bindDeadLetterQueue(dlq, deadLetterExchange, queueName)
        );
    }

    /**
     * Declares source exchange, its DLX, and all consumer queues/bindings for one consumed exchange.
     */
    public static Declarables declarablesForConsumers(String sourceExchangeName, ConsumerQueues... consumers) {
        TopicExchange sourceExchange = topicExchange(sourceExchangeName);
        TopicExchange dlx = deadLetterExchange(sourceExchangeName);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(sourceExchange);
        declarables.add(dlx);
        for (ConsumerQueues consumer : consumers) {
            declarables.add(consumer.queue());
            declarables.add(consumer.dlq());
            declarables.add(consumer.binding());
            declarables.add(consumer.dlqBinding());
        }
        return new Declarables(declarables);
    }
}
