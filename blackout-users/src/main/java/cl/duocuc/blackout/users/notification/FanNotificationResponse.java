package cl.duocuc.blackout.users.notification;

import java.time.Instant;
import java.util.UUID;

public record FanNotificationResponse(
        Long id,
        UUID eventId,
        String eventType,
        String title,
        String message,
        String audience,
        Instant createdAt) {

    static FanNotificationResponse from(FanNotification notification) {
        return new FanNotificationResponse(
                notification.getId(),
                notification.getEventId(),
                notification.getEventType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getAudience(),
                notification.getCreatedAt());
    }
}
