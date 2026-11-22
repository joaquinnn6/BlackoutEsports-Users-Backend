package cl.duocuc.blackout.users.academy;

import cl.duocuc.blackout.users.TestTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestTokens.class)
class AcademyAuditTests {
    @Autowired AcademyRepository applications;
    @Autowired AcademyAuditRepository audit;
    @Autowired AcademyAuditService service;
    @Autowired MockMvc mvc;
    @Test void registraUnaSolaDecisionYRestringeSuConsulta() throws Exception {
        var a=new AcademyApplication();
        a.id=UUID.randomUUID(); a.applicant="audit-"+a.id; a.decision=AcademyApplication.Decision.REJECTED;
        a.decidedAt=Instant.now(); a.decidedBy="staff-1";
        applications.saveAndFlush(a);
        service.record(new AcademyMessaging.DecisionEvent(a.id));
        service.record(new AcademyMessaging.DecisionEvent(a.id));
        assertThat(audit.findById(a.id).orElseThrow().reviewedBy).isEqualTo("staff-1");
        mvc.perform(get("/api/academy/applications/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/academy/applications/audit").header("Authorization","Bearer "+TestTokens.fan()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/academy/applications/audit").header("Authorization","Bearer "+TestTokens.staff()))
            .andExpect(status().isOk());
    }
}
