package cl.duocuc.blackout.users.messaging;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RabbitMessagingProperties.class)
public class RabbitMessagingConfig {

    @Bean
    TopicExchange eventsExchange(RabbitMessagingProperties properties) {
        return new TopicExchange(properties.eventsExchange(), true, false);
    }

    @Bean
    DirectExchange deadLetterExchange(RabbitMessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue fanNotificationsQueue(RabbitMessagingProperties properties) {
        return QueueBuilder.durable(properties.queues().fanNotifications())
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(properties.routing().fanNotificationsDlq())
                .build();
    }

    @Bean
    Queue fanNotificationsDeadLetterQueue(RabbitMessagingProperties properties) {
        return QueueBuilder.durable(properties.queues().fanNotificationsDlq()).build();
    }

    @Bean
    Binding fanNotificationsBinding(
            @Qualifier("fanNotificationsQueue") Queue queue,
            TopicExchange exchange,
            RabbitMessagingProperties properties) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.routing().fanNotifications());
    }

    @Bean
    Binding fanNotificationsDeadLetterBinding(
            @Qualifier("fanNotificationsDeadLetterQueue") Queue queue,
            DirectExchange exchange,
            RabbitMessagingProperties properties) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.routing().fanNotificationsDlq());
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
