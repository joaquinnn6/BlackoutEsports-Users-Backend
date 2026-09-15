package cl.duocuc.blackout.users;
import jakarta.validation.constraints.*;
public record UserRequest(
    @NotBlank @Size(max = 128) @Pattern(regexp = "\\S+", message = "No debe contener espacios") String cognitoSub,
    @NotBlank @Size(max = 100) String displayName,
    @Size(max = 100) String favoriteTeam
) { }
