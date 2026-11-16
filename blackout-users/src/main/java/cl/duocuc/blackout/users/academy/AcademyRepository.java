package cl.duocuc.blackout.users.academy;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface AcademyRepository extends JpaRepository<AcademyApplication, UUID> {
    Optional<AcademyApplication> findByApplicant(String applicant);
    List<AcademyApplication> findAllByOrderBySubmittedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AcademyApplication a where a.id = :id")
    Optional<AcademyApplication> lockById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AcademyApplication> findFirstByPendingPublicationTrueOrderByDecidedAtAsc();
}
