package cl.duocuc.blackout.users;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }
    @GetMapping
    public List<UserResponse> list() { return service.list(); }
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) { return service.get(id); }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) { return service.me(jwt.getSubject()); }
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        var saved = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + saved.id())).body(saved);
    }
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
