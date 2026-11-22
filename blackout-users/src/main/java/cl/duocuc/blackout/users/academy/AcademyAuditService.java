package cl.duocuc.blackout.users.academy;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademyAuditService {
    private final AcademyRepository applications;
    private final AcademyAuditRepository audit;
    public AcademyAuditService(AcademyRepository applications, AcademyAuditRepository audit) {
        this.applications=applications; this.audit=audit;
    }
    @Transactional
    public void record(AcademyMessaging.DecisionEvent event) {
        var application=applications.lockById(event.applicationId()).orElseThrow();
        if(application.decision==AcademyApplication.Decision.PENDING) throw new IllegalStateException("La decisión no existe");
        if(audit.existsById(application.id)) return;
        AcademyAudit entry=new AcademyAudit();
        entry.applicationId=application.id; entry.decision=application.decision.name();
        entry.reviewedBy=application.decidedBy; entry.decidedAt=application.decidedAt;
        entry.recordedAt=Instant.now();
        audit.saveAndFlush(entry);
    }
}
