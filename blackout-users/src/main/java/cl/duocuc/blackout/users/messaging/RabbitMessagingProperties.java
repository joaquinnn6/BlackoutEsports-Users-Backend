package cl.duocuc.blackout.users.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rabbit")
public record RabbitMessagingProperties(
        boolean enabled,
        @NotBlank String eventsExchange,
        @NotBlank String deadLetterExchange,
        @Valid Queues queues,
        @Valid Routing routing,
        @Min(1) int maxRetries) {

    public record Queues(
            @NotBlank String fanNotifications,
            @NotBlank String fanNotificationsDlq) {}

    public record Routing(
            @NotBlank String fanNotifications,
            @NotBlank String fanNotificationsDlq,
            @NotBlank String tournamentCreated) {}
}
