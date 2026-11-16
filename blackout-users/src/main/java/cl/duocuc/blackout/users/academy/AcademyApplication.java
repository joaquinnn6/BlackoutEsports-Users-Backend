package cl.duocuc.blackout.users.academy;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "academy_applications")
public class AcademyApplication {
    public enum Decision { PENDING, ACCEPTED, REJECTED }
    public enum Delivery { NONE, PENDING, SENT, FAILED }

    @Id public UUID id;
    @JsonIgnore @Column(nullable = false, unique = true) public String applicant;
    public String email;
    public String alias;
    public String game;
    public String role;
    public String rankName;
    public String availability;
    @Column(length = 1500) public String motivation;
    public Instant submittedAt;
    @Enumerated(EnumType.STRING) public Decision decision;
    public Instant tryoutAt;
    @Column(length = 500) public String location;
    public Instant decidedAt;
    @JsonIgnore public String decidedBy;
    @Enumerated(EnumType.STRING) public Delivery delivery;
    // La decisión es definitiva. Una fila pendiente representa exactamente un evento por postulación.
    @JsonIgnore public boolean pendingPublication;
}
