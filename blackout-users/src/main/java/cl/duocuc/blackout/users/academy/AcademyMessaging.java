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
        return new Declarables(emails, failed,
                BindingBuilder.bind(emails).to(events).with(routingKey(AcademyApplication.Decision.ACCEPTED)),
                BindingBuilder.bind(emails).to(events).with(routingKey(AcademyApplication.Decision.REJECTED)),
                BindingBuilder.bind(failed).to(deadLetters).with(FAILED_KEY));
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
                    try {
                        if (converter.fromMessage(message) instanceof DecisionEvent event) mail.markFailed(event.applicationId());
                    } finally {
                        new RejectAndDontRequeueRecoverer().recover(message, cause);
                    }
                }).build());
        return factory;
    }
}
