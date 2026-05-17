package com.team27.amazon.user.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventQueueNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig
@ContextConfiguration(classes = {RabbitTopologyConfig.class, RabbitTopologyConfigSpringTest.MockConfig.class})
class RabbitTopologyConfigSpringTest {

    @Configuration
    static class MockConfig {
        @Bean
        ConnectionFactory connectionFactory() {
            return mock(ConnectionFactory.class);
        }
    }

    @Autowired
    private TopicExchange userEventsExchange;

    @Autowired
    private Declarables orderEventConsumers;

    @Test
    void springContextRegistersProducerAndOrderConsumers() {
        assertEquals(EventExchanges.USER_EVENTS, userEventsExchange.getName());
        assertTrue(userEventsExchange.isDurable());
        assertFalse(userEventsExchange.isAutoDelete());
        assertEquals(10, orderEventConsumers.getDeclarables().size());
    }
}

class RabbitTopologyConfigTest {

    @Test
    void userEventsExchange_usesContractNameAndIsDurable() {
        TopicExchange exchange = new RabbitTopologyConfig().userEventsExchange();
        assertEquals(EventExchanges.USER_EVENTS, exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void orderEventConsumers_declaresCompletedAndCancelledQueues() {
        Declarables declarables = new RabbitTopologyConfig().orderEventConsumers();
        long queues = declarables.getDeclarables().stream().filter(Queue.class::isInstance).count();
        assertEquals(4, queues);
        assertTrue(declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .anyMatch(q -> EventQueueNames.USER_ORDER_COMPLETED.equals(q.getName())));
        assertTrue(declarables.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .anyMatch(q -> EventQueueNames.USER_ORDER_CANCELLED.equals(q.getName())));
    }
}
