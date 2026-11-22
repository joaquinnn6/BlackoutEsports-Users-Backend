package cl.duocuc.blackout.users.academy;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="academy_decision_audit")
public class AcademyAudit {
    @Id public UUID applicationId;
    public String decision;
    public String reviewedBy;
    public Instant decidedAt;
    public Instant recordedAt;
}
