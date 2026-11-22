package cl.duocuc.blackout.users.academy;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademyAuditRepository extends JpaRepository<AcademyAudit, UUID> {
    List<AcademyAudit> findAllByOrderByRecordedAtDesc();
}
