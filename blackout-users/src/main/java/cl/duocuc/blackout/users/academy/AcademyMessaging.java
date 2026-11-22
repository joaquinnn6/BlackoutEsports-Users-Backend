package cl.duocuc.blackout.users.academy;

import java.util.UUID;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.rabbit.enabled", havingValue="true", matchIfMissing=true)
public class AcademyMessaging {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AcademyMessaging.class);
    static final String AUDIT_QUEUE = "academy.audit.queue";
    static final String AUDIT_DLQ = "academy.audit.dlq";
    static final String AUDIT_FAILED = "academy.audit.failed";
    static final String EMAIL_QUEUE = "academy.email.queue";
    static final String FAILED_QUEUE = "academy.email.dlq";
    static final String FAILED_KEY = "academy.email.failed";
    public record DecisionEvent(UUID applicationId) {}

    static String routingKey(AcademyApplication.Decision decision) {
        return switch (decision) {
            case ACCEPTED -> "academy.application.accepted";
            case REJECTED -> "academy.application.rejected";
            default -> throw new IllegalArgumentException("La postulación sigue pendiente");
        };
    }

    @Bean
    Declarables academyTopology(TopicExchange events, DirectExchange deadLetters) {
        Queue emails = QueueBuilder.durable(EMAIL_QUEUE).deadLetterExchange(deadLetters.getName())
                .deadLetterRoutingKey(FAILED_KEY).build();
        Queue failed = QueueBuilder.durable(FAILED_QUEUE).build();
        Queue audits=QueueBuilder.durable(AUDIT_QUEUE).deadLetterExchange(deadLetters.getName())
                .deadLetterRoutingKey(AUDIT_FAILED).build();
        Queue auditFailed=QueueBuilder.durable(AUDIT_DLQ).build();
        return new Declarables(emails, failed, audits, auditFailed,
                BindingBuilder.bind(audits).to(events).with("academy.application.*"),
                BindingBuilder.bind(auditFailed).to(deadLetters).with(AUDIT_FAILED),
                BindingBuilder.bind(emails).to(events).with(routingKey(AcademyApplication.Decision.ACCEPTED)),
                BindingBuilder.bind(emails).to(events).with(routingKey(AcademyApplication.Decision.REJECTED)),
                BindingBuilder.bind(failed).to(deadLetters).with(FAILED_KEY));
    }

    @Bean
    SimpleRabbitListenerContainerFactory academyAuditListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connection, MessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connection);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(1000,2,2000)
            .recoverer((message,cause)-> {
                log.error("Auditoría Academy agotó tres intentos; rechazo hacia {}. MessageId={}",
                    AUDIT_DLQ, message.getMessageProperties().getMessageId(), cause);
                new RejectAndDontRequeueRecoverer().recover(message,cause);
            }).build());
        return factory;
    }

    @Bean
    SimpleRabbitListenerContainerFactory academyListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connection, MessageConverter converter, AcademyMailService mail) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connection);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(1000, 2, 2000)
                .recoverer((message, cause) -> {
                    log.error("Correo Academy agotó tres intentos; rechazo hacia {}. MessageId={}",
                        FAILED_QUEUE, message.getMessageProperties().getMessageId(), cause);
                    try {
                        if (converter.fromMessage(message) instanceof DecisionEvent event) mail.markFailed(event.applicationId());
                    } finally {
                        new RejectAndDontRequeueRecoverer().recover(message, cause);
                    }
                }).build());
        return factory;
    }
}
