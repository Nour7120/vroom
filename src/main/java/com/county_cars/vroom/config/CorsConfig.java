package com.county_cars.vroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration.
 * Allowed origins are driven by {@code cors.allowed-origins} (comma-separated).
 * Defaults to {@code *} so local development works out of the box.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private List<String> allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private List<String> allowedHeaders;

    @Value("${cors.exposed-headers:Authorization,Content-Disposition}")
    private List<String> exposedHeaders;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // When allowedOrigins contains "*" we must NOT allow credentials;
        // if specific origins are configured we can enable credentials.
        boolean wildcard = allowedOrigins.contains("*");
        if (wildcard) {
            config.addAllowedOriginPattern("*");
        } else {
            config.setAllowedOrigins(allowedOrigins);
            config.setAllowCredentials(true);
        }

        config.setAllowedMethods(allowedMethods);
        config.setAllowedHeaders(allowedHeaders);
        config.setExposedHeaders(exposedHeaders);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

