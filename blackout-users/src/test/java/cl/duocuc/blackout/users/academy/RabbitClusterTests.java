package cl.duocuc.blackout.users.academy;

import cl.duocuc.blackout.users.TestTokens;
import cl.duocuc.blackout.users.messaging.TournamentCreatedEvent;
import cl.duocuc.blackout.users.notification.FanNotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={
    "app.rabbit.enabled=true", "spring.rabbitmq.addresses=localhost:5672,localhost:5673",
    "spring.rabbitmq.username=blackout", "spring.rabbitmq.password=ci-only-password",
    "academy.mail-consumer-enabled=true", "academy.mail-from=club@example.org",
    "spring.mail.host=localhost", "spring.mail.port=1025",
    "spring.mail.properties.mail.smtp.auth=false", "spring.mail.properties.mail.smtp.starttls.enable=false",
    "spring.mail.properties.mail.smtp.starttls.required=false", "academy.publish-delay-ms=100"
})
@ActiveProfiles("test")
@Import(TestTokens.class)
@EnabledIfSystemProperty(named="rabbit.integration",matches="true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RabbitClusterTests {
    @Autowired AcademyRepository applications;
    @Autowired AcademyAuditRepository audit;
    @Autowired FanNotificationRepository notifications;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @MockitoSpyBean JavaMailSender smtp;
    private final RestClient mailpit=RestClient.create("http://localhost:8025");

    @Test @Order(1)
    void dosDecisionesLleganAlSmtpRealYLaAuditoriaPorColasIndependientes() throws Exception {
        for(var decision:List.of(AcademyApplication.Decision.ACCEPTED,AcademyApplication.Decision.REJECTED)) {
            var a=application(decision);
            applications.saveAndFlush(a);
            await().atMost(Duration.ofSeconds(40)).untilAsserted(()-> {
                assertThat(applications.findById(a.id).orElseThrow().delivery).isEqualTo(AcademyApplication.Delivery.SENT);
                assertThat(audit.findById(a.id)).isPresent();
                JsonNode messages=mailpit.get().uri("/api/v1/messages").retrieve().body(JsonNode.class);
                assertThat(messages.toString()).contains(a.email);
                var summary=java.util.stream.StreamSupport.stream(messages.path("messages").spliterator(),false)
                    .filter(n->n.toString().contains(a.email)).findFirst().orElseThrow();
                JsonNode content=mailpit.get().uri("/api/v1/message/"+summary.path("ID").asText()).retrieve().body(JsonNode.class);
                assertThat(content.path("Text").asText()).contains(decision==AcademyApplication.Decision.ACCEPTED ? "Sala de pruebas" : "otros postulantes");
            });
        }
        var event=new TournamentCreatedEvent(UUID.randomUUID(),"tournament.created",Instant.now(),"ci",
            new TournamentCreatedEvent.TournamentData(1L,"Copa CI","Valorant","Programado","Mañana","2"));
        publish("blackout.events.exchange","tournament.created",event);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(notifications.existsByEventId(event.eventId())).isTrue());
        for(String queue:List.of("fan.notifications.queue","fan.notifications.dlq",AcademyMessaging.EMAIL_QUEUE,
            AcademyMessaging.FAILED_QUEUE,AcademyMessaging.AUDIT_QUEUE,AcademyMessaging.AUDIT_DLQ))
            assertThat(admin.getQueueProperties(queue)).isNotNull();
    }

    @Test @Order(2)
    void falloSmtpHaceTresIntentosAntesDeDlqSinImpedirAuditoria() throws Exception {
        doThrow(new MailSendException("Fallo SMTP controlado de prueba")).when(smtp).send(any(SimpleMailMessage.class));
        var a=application(AcademyApplication.Decision.REJECTED);
        applications.saveAndFlush(a);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()-> {
            assertThat(applications.findById(a.id).orElseThrow().delivery).isEqualTo(AcademyApplication.Delivery.FAILED);
            assertThat(audit.findById(a.id)).isPresent();
        });
        verify(smtp,times(3)).send(any(SimpleMailMessage.class));
        Message failed=rabbit.receive(AcademyMessaging.FAILED_QUEUE,10000);
        assertThat(failed).isNotNull();
        assertThat(failed.getMessageProperties().getHeaders()).containsKey("x-death");
        assertThat(new String(failed.getBody(),java.nio.charset.StandardCharsets.UTF_8)).contains(a.id.toString());
    }

    @Test @Order(3)
    void mensajesInvalidosLleganALasOtrasDosDlq() throws Exception {
        publish("",AcademyMessaging.AUDIT_QUEUE,new AcademyMessaging.DecisionEvent(UUID.randomUUID()));
        Message auditFailed=rabbit.receive(AcademyMessaging.AUDIT_DLQ,15000);
        assertThat(auditFailed).isNotNull();
        assertThat(auditFailed.getMessageProperties().getHeaders()).containsKey("x-death");
        publish("blackout.events.exchange","tournament.created",new TournamentCreatedEvent(UUID.randomUUID(),"tournament.created",Instant.now(),"ci",null));
        Message fanFailed=rabbit.receive("fan.notifications.dlq",15000);
        assertThat(fanFailed).isNotNull();
        assertThat(fanFailed.getMessageProperties().getHeaders()).containsKey("x-death");
    }

    AcademyApplication application(AcademyApplication.Decision decision) {
        var a=new AcademyApplication(); a.id=UUID.randomUUID(); a.applicant="ci-"+a.id;
        a.email=a.id+"@example.org"; a.alias="Prueba"; a.game="Valorant"; a.decision=decision;
        a.delivery=AcademyApplication.Delivery.PENDING; a.pendingPublication=true;
        a.decidedAt=Instant.now(); a.decidedBy="staff-ci"; a.tryoutAt=Instant.now().plusSeconds(86400);
        a.location="Sala de pruebas";
        return a;
    }
    void publish(String exchange,String key,Object event) throws Exception {
        var confirmation=new CorrelationData();
        rabbit.convertAndSend(exchange,key,event,confirmation);
        assertThat(confirmation.getFuture().get(5,TimeUnit.SECONDS).isAck()).isTrue();
        assertThat(confirmation.getReturned()).isNull();
    }
}
