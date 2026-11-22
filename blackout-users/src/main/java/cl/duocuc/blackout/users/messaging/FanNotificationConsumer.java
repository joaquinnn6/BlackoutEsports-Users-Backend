package cl.duocuc.blackout.users.messaging;

import cl.duocuc.blackout.users.notification.FanNotificationService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
public class FanNotificationConsumer {
    static final String RETRY_HEADER = "x-blackout-retry-count";

    private final FanNotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessagingProperties properties;

    @RabbitListener(
            queues = "${app.rabbit.queues.fan-notifications}",
            containerFactory = "rabbitListenerContainerFactory")
    public void consume(TournamentCreatedEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            notificationService.registerTournamentCreated(event);
            channel.basicAck(deliveryTag, false);
            log.info("Evento {} procesado y confirmado", event.eventId());
        } catch (Exception exception) {
            handleFailure(event, message, channel, deliveryTag, exception);
        }
    }

    private void handleFailure(
            TournamentCreatedEvent event,
            Message message,
            Channel channel,
            long deliveryTag,
            Exception exception) throws IOException {
        int previousRetries = retryCount(message);
        int currentAttempt = previousRetries + 1;

        if (currentAttempt < properties.maxRetries()) {
            try {
                var confirmation=new org.springframework.amqp.rabbit.connection.CorrelationData();
                rabbitTemplate.convertAndSend(
                        properties.eventsExchange(),
                        properties.routing().tournamentCreated(),
                        event,
                        outgoing -> {
                            outgoing.getMessageProperties().setHeader(RETRY_HEADER, currentAttempt);
                            return outgoing;
                        }, confirmation);
                if(!confirmation.getFuture().get(5,java.util.concurrent.TimeUnit.SECONDS).isAck() || confirmation.getReturned()!=null)
                    throw new AmqpException("RabbitMQ no confirmó el reintento");
                channel.basicAck(deliveryTag, false);
                log.warn("Evento {} reenviado; intento {} de {}", event.eventId(), currentAttempt + 1,
                        properties.maxRetries(), exception);
            } catch (Exception publishException) {
                if(publishException instanceof InterruptedException) Thread.currentThread().interrupt();
                channel.basicNack(deliveryTag, false, true);
                log.error("No fue posible reenviar el evento {}; se devuelve a la cola", event.eventId(),
                        publishException);
            }
            return;
        }

        channel.basicNack(deliveryTag, false, false);
        log.error("Evento {} rechazado después de {} intentos; RabbitMQ lo enviará a la DLQ",
                event.eventId(), properties.maxRetries(), exception);
    }

    private static int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
