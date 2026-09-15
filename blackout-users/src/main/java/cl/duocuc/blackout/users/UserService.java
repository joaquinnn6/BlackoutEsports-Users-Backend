package cl.duocuc.blackout.users;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;
    public UserService(UserRepository repository) { this.repository = repository; }
    public List<UserResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }
    public UserResponse get(Long id) { return UserResponse.from(find(id)); }
    public UserResponse me(String sub) {
        return repository.findByCognitoSub(sub).map(UserResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil todavía no registrado"));
    }
    @Transactional
    public UserResponse create(UserRequest request) {
        UserProfile user = new UserProfile();
        user.setCognitoSub(request.cognitoSub());
        apply(user, request);
        return UserResponse.from(repository.saveAndFlush(user));
    }
    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        UserProfile user = find(id);
        if (!user.getCognitoSub().equals(request.cognitoSub()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La identidad Cognito de un perfil es inmutable");
        apply(user, request);
        return UserResponse.from(repository.saveAndFlush(user));
    }
    @Transactional
    public void delete(Long id) { repository.delete(find(id)); }
    private UserProfile find(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
    }
    private void apply(UserProfile user, UserRequest request) {
        user.setDisplayName(request.displayName().trim());
        user.setFavoriteTeam(request.favoriteTeam() == null ? null : request.favoriteTeam().trim());
    }
}
