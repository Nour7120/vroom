package com.county_cars.vroom.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Central Spring Security configuration.
 *
 * <p><b>JWT flow:</b> Every request is validated as an OAuth2 Resource Server using the
 * Keycloak JWKS endpoint. Permissions are resolved via {@link #loadPermissionsFromDatabase}
 * (currently returns a fixed dev list — replace with a real DB call when ready).</p>
 *
 * <p><b>Public paths:</b> Registration, resend-verification, Swagger, and Actuator
 * health endpoints are fully open — no token required.</p>
 *
 * <p><b>CORS:</b> Configured via {@link com.county_cars.vroom.config.CorsConfig} and
 * driven by {@code cors.*} properties.</p>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize / @PostAuthorize on all @Service / @Controller beans
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;

    private static final String[] PUBLIC_PATHS = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/info",
            "/api/v1/auth/**",
            "/internal/keycloak/events",
            "/ws/chat"          // WebSocket upgrade — JWT validated in ChatHandshakeInterceptor
    };

    // ── Main filter chain ─────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ── CORS ─────────────────────────────────────────────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // ── CSRF disabled – stateless JWT API ────────────────────────────
                .csrf(AbstractHttpConfigurer::disable)

                // ── Stateless sessions ───────────────────────────────────────────
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ── Authorization rules ──────────────────────────────────────────
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )

                // ── OAuth2 Resource Server ───────────────────────────────────────
                // customJWTAuthenticationConverter:
                //   • reads email + mobile_number claims from the JWT
                //   • loads matching permissions from the DB (see loadPermissionsFromDatabase)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(customJWTAuthenticationConverter()))
                );

        return http.build();
    }

    // ── JWT → Authentication converter ───────────────────────────────────────────

    private JwtAuthenticationConverter customJWTAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String mobileNumber = jwt.getClaimAsString("mobile_number");
        String email        = jwt.getClaimAsString("email");
        log.debug("JWT claims resolved — email={}, mobile_number={}", email, mobileNumber);

        List<String> permissions = loadPermissionsFromDatabase(email);
        log.debug("Granted permissions for email={}: {}", email, permissions);

        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * TODO: Replace with a real {@code UserGroupRepository} query once the auth
     * module is wired up (see {@code KeycloakJwtAuthenticationConverter} for the
     * production implementation pattern).
     *
     * <p>Current behaviour: returns a fixed set of dev permissions so the app starts
     * and all endpoints are reachable without a running Keycloak + populated DB.</p>
     */
    private List<String> loadPermissionsFromDatabase(String email) {
        return List.of(
                "get.user.types",
                "get.user.type.by.id",
                "get.groups",
                "get.group.by.id",
                "add.group",
                "update.group.by.id",
                "delete.group.by.id",
                "link.permissions.to.group",
                "get.groups.with.permissions",
                "get.permissions",
                "get.permission.by.id",
                "add.permission",
                "update.permission.by.id",
                "delete.permission.by.id"
        );
    }
}
