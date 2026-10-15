package cl.duocuc.blackout.users.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FanNotificationRepository extends JpaRepository<FanNotification, Long> {
    boolean existsByEventId(UUID eventId);
    List<FanNotification> findAllByOrderByCreatedAtDesc();
}
