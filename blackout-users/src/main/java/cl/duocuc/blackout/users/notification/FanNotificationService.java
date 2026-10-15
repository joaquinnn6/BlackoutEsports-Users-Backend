package cl.duocuc.blackout.users.notification;

import cl.duocuc.blackout.users.messaging.TournamentCreatedEvent;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FanNotificationService {
    private final FanNotificationRepository repository;

    public List<FanNotificationResponse> findAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(FanNotificationResponse::from)
                .toList();
    }

    @Transactional
    public void registerTournamentCreated(TournamentCreatedEvent event) {
        if (repository.existsByEventId(event.eventId())) return;

        FanNotification notification = new FanNotification();
        notification.setEventId(event.eventId());
        notification.setEventType(event.eventType());
        notification.setTitle("Nuevo torneo: " + event.tournament().name());
        notification.setMessage(String.format(
                "Blackout Esports participará en %s de %s. Fecha: %s.",
                event.tournament().name(),
                event.tournament().game(),
                event.tournament().date()));
        notification.setAudience("ALL_FANS");
        notification.setCreatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        repository.saveAndFlush(notification);
    }
}
