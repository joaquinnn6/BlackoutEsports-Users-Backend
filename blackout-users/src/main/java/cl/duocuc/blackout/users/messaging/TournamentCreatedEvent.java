package cl.duocuc.blackout.users.messaging;

import java.time.Instant;
import java.util.UUID;

public record TournamentCreatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String source,
        TournamentData tournament) {

    public record TournamentData(
            Long id,
            String name,
            String game,
            String status,
            String date,
            String teams) {}
}
