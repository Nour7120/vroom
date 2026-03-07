package com.county_cars.vroom.modules.keycloak.config;

import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Produces a singleton {@link Keycloak} admin-client bean and a {@link RestClient}
 * used for direct HTTP calls to Keycloak endpoints (e.g. credential verification).
 */
@Configuration
@RequiredArgsConstructor
public class KeycloakAdminClientConfig {

    private final KeycloakAdminProperties props;

    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .realm("master")                       // always authenticate against master
                .grantType(OAuth2Constants.PASSWORD)
                .clientId("admin-cli")
                .username(props.getUsername())
                .password(props.getPassword())
                .build();
    }

    /**
     * A generic {@link RestClient} used by {@code KeycloakAdminService}
     * for calls that the Keycloak Admin Client SDK does not cover
     * (e.g. ROPC token endpoint for credential verification).
     */
    @Bean
    public RestClient keycloakRestClient() {
        return RestClient.builder().build();
    }
}

