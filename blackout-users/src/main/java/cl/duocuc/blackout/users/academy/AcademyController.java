package cl.duocuc.blackout.users.academy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/academy")
public class AcademyController {
    private final AcademyRepository applications;
    private final String userInfoUrl;
    private final RestClient cognito;

    AcademyController(AcademyRepository applications, RestClient.Builder builder, @Value("${academy.cognito-userinfo-url:}") String userInfoUrl) {
        this.cognito = builder.build();
        this.applications = applications;
        this.userInfoUrl = userInfoUrl;
    }

    public record Form(@NotBlank @Size(max=100) String alias, @NotBlank @Size(max=100) String game,
            @NotBlank @Size(max=100) String role, @NotBlank @Size(max=100) String rankName,
            @NotBlank @Size(max=250) String availability, @NotBlank @Size(max=1500) String motivation) {}
    public record Review(@NotNull AcademyApplication.Decision decision, Instant tryoutAt, @Size(max=500) String location) {}
    record Identity(String sub, String email, Boolean email_verified) {}

    @GetMapping("/me")
    public ResponseEntity<AcademyApplication> mine(@AuthenticationPrincipal Jwt jwt) {
        return applications.findByApplicant(jwt.getSubject()).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademyApplication apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Form form) {
        if (applications.findByApplicant(jwt.getSubject()).isPresent()) throw duplicate();
        if (!userInfoUrl.startsWith("https://")) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta configurar Cognito UserInfo");
        Identity identity;
        try {
            identity = cognito.get().uri(userInfoUrl).headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve().body(Identity.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cognito no pudo confirmar el correo");
        }
        if (identity == null || !jwt.getSubject().equals(identity.sub()) || !Boolean.TRUE.equals(identity.email_verified())
                || identity.email() == null || identity.email().isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifica tu correo en Cognito antes de postular");
        var a = new AcademyApplication();
        a.id = UUID.randomUUID();
        a.applicant = jwt.getSubject();
        a.email = identity.email();
        a.alias = form.alias().trim();
        a.game = form.game().trim();
        a.role = form.role().trim();
        a.rankName = form.rankName().trim();
        a.availability = form.availability().trim();
        a.motivation = form.motivation().trim();
        a.submittedAt = Instant.now();
        a.decision = AcademyApplication.Decision.PENDING;
        a.delivery = AcademyApplication.Delivery.NONE;
        try { return applications.saveAndFlush(a); }
        catch (DataIntegrityViolationException e) {
            if (applications.findByApplicant(jwt.getSubject()).isPresent()) throw duplicate();
            throw e;
        }
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya enviaste una postulación");
    }

    @GetMapping("/applications")
    public List<AcademyApplication> all() { return applications.findAllByOrderBySubmittedAtDesc(); }

    @Transactional
    @PutMapping("/applications/{id}/decision")
    public AcademyApplication review(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Review review) {
        var a = applications.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (a.decision != AcademyApplication.Decision.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya se resolvió esta postulación");
        if (review.decision() == AcademyApplication.Decision.PENDING)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una decisión");
        if (review.decision() == AcademyApplication.Decision.ACCEPTED) {
            if (review.tryoutAt() == null || !review.tryoutAt().isAfter(Instant.now()) || review.location() == null || review.location().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica una fecha futura y lugar o enlace");
            a.tryoutAt = review.tryoutAt();
            a.location = review.location().trim();
        }
        a.decision = review.decision();
        a.decidedBy = jwt.getSubject();
        a.decidedAt = Instant.now();
        a.delivery = AcademyApplication.Delivery.PENDING;
        a.pendingPublication = true;
        return a;
    }
}
