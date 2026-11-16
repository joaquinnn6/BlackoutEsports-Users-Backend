package cl.duocuc.blackout.users.academy;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AcademyMailTests {
    @ParameterizedTest
    @EnumSource(value=AcademyApplication.Decision.class, names={"ACCEPTED","REJECTED"})
    void preparaElMensajeYReconoceUnaEntregaRepetida(AcademyApplication.Decision decision) {
        var applications = mock(AcademyRepository.class);
        var smtp = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> providers = mock(ObjectProvider.class);
        when(providers.getObject()).thenReturn(smtp);
        var a = new AcademyApplication();
        a.id = UUID.randomUUID();
        a.decision = decision;
        a.email = "fan@example.org";
        a.alias = "Camila";
        a.game = "Valorant";
        a.tryoutAt = Instant.parse("2027-01-10T21:00:00Z");
        a.location = "Sala 3";
        when(applications.lockById(a.id)).thenReturn(Optional.of(a));
        var service = new AcademyMailService(applications, providers, "club@example.org");
        var event = new AcademyMessaging.DecisionEvent(a.id);
        service.deliver(event);
        service.deliver(event);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(smtp).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(a.email);
        assertThat(message.getValue().getText()).contains(decision == AcademyApplication.Decision.ACCEPTED ? "18:00" : "otros postulantes");
        assertThat(a.delivery).isEqualTo(AcademyApplication.Delivery.SENT);
    }
}
