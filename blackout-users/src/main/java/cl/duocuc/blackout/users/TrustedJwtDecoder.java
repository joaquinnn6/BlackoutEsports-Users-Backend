package cl.duocuc.blackout.users;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Arrays;
import org.springframework.security.oauth2.jwt.*;

/** El issuer sin verificar solamente selecciona un validador de la lista configurada. */
public class TrustedJwtDecoder implements JwtDecoder {
    private final SecurityProperties properties;
    private final JwtDecoder entra;
    private final JwtDecoder cognito;

    public TrustedJwtDecoder(SecurityProperties properties) {
        this(properties,
            new SupplierJwtDecoder(() -> NimbusJwtDecoder.withIssuerLocation(properties.entraIssuer()).build()),
            new SupplierJwtDecoder(() -> NimbusJwtDecoder.withIssuerLocation(properties.cognitoIssuer()).build()));
    }

    TrustedJwtDecoder(SecurityProperties properties, JwtDecoder entra, JwtDecoder cognito) {
        this.properties = properties;
        this.entra = entra;
        this.cognito = cognito;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            boolean isEntra = configured(properties.entraIssuer()) && properties.entraIssuer().equals(issuer);
            boolean isCognito = configured(properties.cognitoIssuer()) && properties.cognitoIssuer().equals(issuer);
            if (isEntra == isCognito) throw new BadJwtException("Emisor no autorizado o ambiguo");
            if (isEntra && (!configured(properties.entraAudience()) || !configured(properties.entraScope())))
                throw new BadJwtException("Configuración de Entra incompleta");
            if (isCognito && !configured(properties.cognitoClientId()))
                throw new BadJwtException("Configuración de Cognito incompleta");
            Jwt jwt = (isEntra ? entra : cognito).decode(token);
            var result = JwtValidators.createDefaultWithIssuer(issuer).validate(jwt);
            if (result.hasErrors() || jwt.getExpiresAt() == null || !configured(jwt.getSubject()))
                throw new BadJwtException("Token expirado o con claims obligatorios ausentes");
            if (isEntra) {
                String scope = jwt.getClaimAsString("scp");
                if (jwt.getAudience() == null || !jwt.getAudience().contains(properties.entraAudience()) || scope == null ||
                    !Arrays.asList(scope.split(" ")).contains(properties.entraScope()))
                    throw new BadJwtException("Audiencia o scope de API inválido");
            } else if (!"access".equals(jwt.getClaimAsString("token_use")) ||
                       !properties.cognitoClientId().equals(jwt.getClaimAsString("client_id"))) {
                throw new BadJwtException("Se requiere un access token del cliente Cognito autorizado");
            }
            return jwt;
        } catch (ParseException | IllegalArgumentException exception) {
            throw new BadJwtException("Token inválido", exception);
        }
    }

    private static boolean configured(String value) { return value != null && !value.isBlank(); }
}
