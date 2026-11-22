package cl.duocuc.blackout.users.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import org.springframework.amqp.rabbit.connection.CorrelationData;

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
        doAnswer(invocation -> {
            CorrelationData confirmation=invocation.getArgument(4);
            confirmation.getFuture().complete(new CorrelationData.Confirm(true,null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class),any(String.class),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
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
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(channel).basicAck(22L, false);
    }

    @Test
    void rejectsToDeadLetterQueueAfterTheRetryLimit() throws Exception {
        Message message = message(23L, 2);
        doThrow(new IllegalStateException("fallo definitivo")).when(service).registerTournamentCreated(event);

        consumer.consume(event, message, channel);

        verify(channel).basicNack(23L, false, false);
    }

    @Test
    void conservaOriginalCuandoElBrokerRechazaLaRepublicacion() throws Exception {
        doThrow(new IllegalStateException("fallo temporal")).when(service).registerTournamentCreated(event);
        doAnswer(invocation -> {
            CorrelationData confirmation=invocation.getArgument(4);
            confirmation.getFuture().complete(new CorrelationData.Confirm(false,"nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class),any(String.class),any(Object.class),any(MessagePostProcessor.class),any(CorrelationData.class));
        consumer.consume(event,message(24L,0),channel);
        verify(channel).basicNack(24L,false,true);
        org.mockito.Mockito.verify(channel,org.mockito.Mockito.never()).basicAck(24L,false);
    }

    private static Message message(long deliveryTag, int retries) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        if (retries > 0) properties.setHeader(FanNotificationConsumer.RETRY_HEADER, retries);
        return new Message(new byte[0], properties);
    }
}
