package cl.duocuc.blackout.users.notification;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Novedades generadas de forma asíncrona para los fans")
public class FanNotificationController {
    private final FanNotificationService service;

    @GetMapping
    public List<FanNotificationResponse> findAll() {
        return service.findAll();
    }
}
