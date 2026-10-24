package win.huansha.moneipe.api.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Seguridad perimetral del gateway.
 *
 * <p>El gateway es el único punto de entrada del front: valida el JWT de Keycloak
 * aquí (validación perimetral) y lo reenvía a los microservicios internos con el
 * header {@code Authorization} intacto (token relay pasivo).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // Sin sesión: cada petición se autentica solo con el estado del JWT (stateless)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // CORS integrado con Security: los preflight OPTIONS no piden token
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        // Salud visible para Docker/monitoreo sin token
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Todo lo demás exige JWT válido
                        .anyExchange().authenticated())
                // Resource server reactivo: valida firma + issuer + expiración
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .build();
    }
}
