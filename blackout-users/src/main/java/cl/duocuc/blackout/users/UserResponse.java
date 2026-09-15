package cl.duocuc.blackout.users;
public record UserResponse(Long id, String cognitoSub, String displayName, String favoriteTeam) {
    static UserResponse from(UserProfile user) {
        return new UserResponse(user.getId(), user.getCognitoSub(), user.getDisplayName(), user.getFavoriteTeam());
    }
}
