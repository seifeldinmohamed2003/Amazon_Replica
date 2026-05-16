package com.team27.amazon.contracts.rabbit;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventQueueNames;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConsumerTopologyTest {

    @Test
    void consumerQueue_wiresDeadLetterArguments() {
        Queue queue = RabbitConsumerTopology.consumerQueue(
                EventQueueNames.SHIPPING_ORDER_PLACED, EventExchanges.ORDER_EVENTS);

        assertEquals(EventQueueNames.SHIPPING_ORDER_PLACED, queue.getName());
        assertTrue(queue.isDurable());

        Map<String, Object> args = queue.getArguments();
        assertEquals(EventExchanges.deadLetterExchange(EventExchanges.ORDER_EVENTS), args.get("x-dead-letter-exchange"));
        assertEquals("shipping.order-placed.dlq", args.get("x-dead-letter-routing-key"));
    }

    @Test
    void deadLetterExchange_usesDlxSuffix() {
        assertEquals("order.events.dlx", EventExchanges.deadLetterExchange(EventExchanges.ORDER_EVENTS));
    }

    @Test
    void declarablesForConsumers_includesExchangeDlxAndFourDeclarablesPerQueue() {
        Declarables declarables = RabbitConsumerTopology.declarablesForConsumers(
                EventExchanges.ORDER_EVENTS,
                RabbitConsumerTopology.consumer(
                        EventQueueNames.USER_ORDER_COMPLETED,
                        EventExchanges.ORDER_EVENTS,
                        RabbitConsumerTopology.topicExchange(EventExchanges.ORDER_EVENTS),
                        RabbitConsumerTopology.deadLetterExchange(EventExchanges.ORDER_EVENTS),
                        EventRoutingKeys.ORDER_COMPLETED)
        );
        assertEquals(6, declarables.getDeclarables().size());
        long queueCount = declarables.getDeclarables().stream().filter(Queue.class::isInstance).count();
        assertEquals(2, queueCount);
    }
}
