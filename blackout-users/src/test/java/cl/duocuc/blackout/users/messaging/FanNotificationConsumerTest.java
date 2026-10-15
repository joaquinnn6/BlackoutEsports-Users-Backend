package cl.duocuc.blackout.users.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cl.duocuc.blackout.users.notification.FanNotificationService;
import com.rabbitmq.client.Channel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class FanNotificationConsumerTest {
    private FanNotificationService service;
    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private FanNotificationConsumer consumer;
    private TournamentCreatedEvent event;

    @BeforeEach
    void setUp() {
        service = mock(FanNotificationService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        RabbitMessagingProperties properties = new RabbitMessagingProperties(
                true,
                "blackout.events.exchange",
                "blackout.dead-letter.exchange",
                new RabbitMessagingProperties.Queues("fan.notifications.queue", "fan.notifications.dlq"),
                new RabbitMessagingProperties.Routing("tournament.#", "fan.notifications.failed", "tournament.created"),
                3);
        consumer = new FanNotificationConsumer(service, rabbitTemplate, properties);
        event = new TournamentCreatedEvent(
                UUID.randomUUID(),
                "tournament.created",
                Instant.now(),
                "blackout-tournaments",
                new TournamentCreatedEvent.TournamentData(1L, "Copa Blackout", "Valorant", "Programado",
                        "25 OCT 2026", "16 equipos"));
    }

    @Test
    void acknowledgesSuccessfullyProcessedMessages() throws Exception {
        Message message = message(21L, 0);

        consumer.consume(event, message, channel);

        verify(service).registerTournamentCreated(event);
        verify(channel).basicAck(21L, false);
    }

    @Test
    void republishesBeforeReachingTheRetryLimit() throws Exception {
        Message message = message(22L, 0);
        doThrow(new IllegalStateException("fallo temporal")).when(service).registerTournamentCreated(event);

        consumer.consume(event, message, channel);

        verify(rabbitTemplate).convertAndSend(
                eq("blackout.events.exchange"),
                eq("tournament.created"),
                eq(event),
                any(MessagePostProcessor.class));
        verify(channel).basicAck(22L, false);
    }

    @Test
    void rejectsToDeadLetterQueueAfterTheRetryLimit() throws Exception {
        Message message = message(23L, 2);
        doThrow(new IllegalStateException("fallo definitivo")).when(service).registerTournamentCreated(event);

        consumer.consume(event, message, channel);

        verify(channel).basicNack(23L, false, false);
    }

    private static Message message(long deliveryTag, int retries) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        if (retries > 0) properties.setHeader(FanNotificationConsumer.RETRY_HEADER, retries);
        return new Message(new byte[0], properties);
    }
}
