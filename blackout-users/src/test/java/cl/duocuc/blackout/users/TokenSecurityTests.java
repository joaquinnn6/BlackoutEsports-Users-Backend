package cl.duocuc.blackout.users;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.assertj.core.api.Assertions.*;
class TokenSecurityTests {
    @Test void aceptaAccessTokensDeAmbosProveedores() throws Exception {
        var decoder = TestTokens.decoder();
        assertThat(decoder.decode(TestTokens.admin()).getSubject()).isEqualTo("fan-1");
        assertThat(decoder.decode(TestTokens.fan()).getSubject()).isEqualTo("fan-1");
    }
    @Test void rechazaFirmaFalsa() throws Exception {
        var token = TestTokens.signed(TestTokens.claims(TestTokens.ENTRA), TestTokens.key());
        assertThatThrownBy(() -> TestTokens.decoder().decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void rechazaTokensIncorrectos() throws Exception {
        var invalid = List.of(
            TestTokens.signed(TestTokens.claims("https://untrusted.example")),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).audience("https://graph.microsoft.com")),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).claim("scp", null)),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).claim("scp", "User.Read")),
            TestTokens.signed(TestTokens.claims(TestTokens.COGNITO).claim("token_use", "id")),
            TestTokens.signed(TestTokens.claims(TestTokens.COGNITO).claim("client_id", "other-client")),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).expirationTime(Date.from(Instant.now().minusSeconds(300)))),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).expirationTime(null)),
            TestTokens.signed(TestTokens.claims(TestTokens.ENTRA).subject(null)),
            "token-invalido");
        var decoder = TestTokens.decoder();
        for (String token : invalid)
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
    @Test void sinConfiguracionRechazaAcceso() throws Exception {
        var decoder = new TrustedJwtDecoder(new SecurityProperties("", "", "", "", ""));
        assertThatThrownBy(() -> decoder.decode(TestTokens.admin())).isInstanceOf(JwtException.class);
    }
    @Test void cognitoNoPuedeHeredarRolesAdministrativos() throws Exception {
        var jwt = TestTokens.decoder().decode(TestTokens.fan());
        assertThat(SecurityConfig.authentication(jwt, TestTokens.PROPERTIES).getAuthorities())
            .extracting(Object::toString).containsExactly("ROLE_Fan");
    }
}
