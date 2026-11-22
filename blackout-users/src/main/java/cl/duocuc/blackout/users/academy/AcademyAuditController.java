package cl.duocuc.blackout.users.academy;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/academy/applications/audit")
public class AcademyAuditController {
    private final AcademyAuditRepository audit;
    public AcademyAuditController(AcademyAuditRepository audit) { this.audit=audit; }
    @GetMapping public List<AcademyAudit> list() { return audit.findAllByOrderByRecordedAtDesc(); }
}
