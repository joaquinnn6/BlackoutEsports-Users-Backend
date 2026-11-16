package cl.duocuc.blackout.users.academy;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="app.rabbit.enabled", havingValue="true", matchIfMissing=true)
public class AcademyMailService {
    private static final DateTimeFormatter CHILE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL"))
            .withZone(ZoneId.of("America/Santiago"));
    private final AcademyRepository applications;
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    AcademyMailService(AcademyRepository applications, ObjectProvider<JavaMailSender> senders, @Value("${academy.mail-from:}") String from) {
        this.applications = applications;
        this.senders = senders;
        this.from = from;
    }

    @Transactional
    @RabbitListener(queues=AcademyMessaging.EMAIL_QUEUE, containerFactory="academyListenerFactory",
            autoStartup="${academy.mail-consumer-enabled:false}")
    public void deliver(AcademyMessaging.DecisionEvent event) {
        var a = applications.lockById(event.applicationId()).orElseThrow();
        if (a.delivery == AcademyApplication.Delivery.SENT) return;
        if (a.decision == AcademyApplication.Decision.PENDING) throw new IllegalStateException("Sin decisión");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(a.email);
        if (a.decision == AcademyApplication.Decision.ACCEPTED) {
            message.setSubject("Blackout Academy · Invitación a tryout");
            message.setText("Hola, " + a.alias + ". Te invitamos al tryout de " + a.game + " el "
                    + CHILE.format(a.tryoutAt) + " (hora de Santiago de Chile). Lugar o enlace: " + a.location);
        } else {
            message.setSubject("Blackout Academy · Resultado de tu postulación");
            message.setText("Hola, " + a.alias + ". Gracias por postular. En esta convocatoria decidimos continuar con otros postulantes. Te invitamos a participar en una próxima convocatoria.");
        }
        if (from.isBlank()) throw new IllegalStateException("Falta el remitente de Academy");
        senders.getObject().send(message);
        a.delivery = AcademyApplication.Delivery.SENT;
    }

    @Transactional
    public void markFailed(UUID id) {
        applications.lockById(id).filter(a -> a.delivery == AcademyApplication.Delivery.PENDING)
                .ifPresent(a -> a.delivery = AcademyApplication.Delivery.FAILED);
    }
}
