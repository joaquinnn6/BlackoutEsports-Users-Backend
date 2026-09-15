package cl.duocuc.blackout.users;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
@Entity
@Table(name = "user_profiles")
@Getter @Setter
public class UserProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 128)
    private String cognitoSub;
    @Column(nullable = false, length = 100)
    private String displayName;
    @Column(length = 100)
    private String favoriteTeam;
}
