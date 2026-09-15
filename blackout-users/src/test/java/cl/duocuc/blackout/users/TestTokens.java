package cl.duocuc.blackout.users;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@TestConfiguration
class TestTokens {
    static final String ENTRA = "https://login.microsoftonline.com/test-tenant/v2.0";
    static final String COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/test-pool";
    static final SecurityProperties PROPERTIES = new SecurityProperties(ENTRA, "test-api",
        "access_as_user", COGNITO, "test-fan-client");
    static final RSAKey KEY = key();
    static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (JOSEException error) { throw new IllegalStateException(error); }
    }
    @Bean @Primary
    JwtDecoder testJwtDecoder() throws JOSEException { return decoder(); }
    static TrustedJwtDecoder decoder() throws JOSEException {
        return new TrustedJwtDecoder(PROPERTIES,
            NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build(),
            NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build());
    }
    static JWTClaimsSet.Builder claims(String issuer) {
        return new JWTClaimsSet.Builder().issuer(issuer).subject("fan-1")
            .issueTime(Date.from(Instant.now().minusSeconds(5)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .audience("test-api").claim("scp", "access_as_user")
            .claim("roles", List.of("Admin")).claim("token_use", "access").claim("client_id", "test-fan-client");
    }
    static String signed(JWTClaimsSet.Builder claims) throws JOSEException {
        return signed(claims, KEY);
    }
    static String signed(JWTClaimsSet.Builder claims, RSAKey key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    static String admin() throws JOSEException { return signed(claims(ENTRA)); }
    static String staff() throws JOSEException { return signed(claims(ENTRA).claim("roles", List.of("Staff"))); }
    static String fan() throws JOSEException { return signed(claims(COGNITO)); }
}
