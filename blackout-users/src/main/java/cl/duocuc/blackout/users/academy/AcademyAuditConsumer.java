package cl.duocuc.blackout.users.academy;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.rabbit.enabled",havingValue="true",matchIfMissing=true)
public class AcademyAuditConsumer {
    private final AcademyAuditService audit;
    public AcademyAuditConsumer(AcademyAuditService audit) { this.audit=audit; }
    @RabbitListener(queues=AcademyMessaging.AUDIT_QUEUE, containerFactory="academyAuditListenerFactory")
    public void consume(AcademyMessaging.DecisionEvent event) { audit.record(event); }
}
