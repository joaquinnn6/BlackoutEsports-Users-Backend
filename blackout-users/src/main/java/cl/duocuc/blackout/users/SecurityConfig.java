package cl.duocuc.blackout.users;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) { return new TrustedJwtDecoder(properties); }

    static JwtAuthenticationToken authentication(Jwt jwt, SecurityProperties properties) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String issuer = jwt.getClaimAsString("iss");
        if (properties.entraIssuer().equals(issuer)) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) roles.stream().filter(role -> role.equals("Admin") || role.equals("Staff"))
                .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        } else if (properties.cognitoIssuer().equals(issuer)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_Fan"));
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityProperties properties) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/me").hasRole("Fan")
                .requestMatchers("/api/users", "/api/users/**").hasAnyRole("Admin", "Staff")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                jwt.jwtAuthenticationConverter(token -> authentication(token, properties))))
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(origins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
