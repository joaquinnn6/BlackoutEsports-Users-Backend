package cl.duocuc.blackout.users.academy;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="app.rabbit.enabled", havingValue="true", matchIfMissing=true)
public class AcademyPublisher {
    private final AcademyRepository applications;
    private final RabbitTemplate rabbit;
    private final TopicExchange exchange;

    AcademyPublisher(AcademyRepository applications, RabbitTemplate rabbit, TopicExchange exchange) {
        this.applications = applications;
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString="${academy.publish-delay-ms:2000}")
    @Transactional(rollbackFor=Exception.class)
    public void publishNext() throws Exception {
        var pending = applications.findFirstByPendingPublicationTrueOrderByDecidedAtAsc();
        if (pending.isEmpty()) return;
        var application = pending.get();
        var confirmation = new CorrelationData();
        rabbit.convertAndSend(exchange.getName(), AcademyMessaging.routingKey(application.decision),
                new AcademyMessaging.DecisionEvent(application.id), confirmation);
        if (!confirmation.getFuture().get(5, TimeUnit.SECONDS).isAck() || confirmation.getReturned() != null)
            throw new IllegalStateException("RabbitMQ no confirmó el enrutamiento de la decisión");
        application.pendingPublication = false;
    }
}
