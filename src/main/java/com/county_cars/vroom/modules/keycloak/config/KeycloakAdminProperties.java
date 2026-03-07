package com.county_cars.vroom.modules.keycloak.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds all {@code keycloak.admin.*} properties from application.properties.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {

    /** Keycloak base URL — e.g. {@code http://localhost:8081} */
    private String serverUrl;

    /** The realm that contains the application's users */
    private String realm;

    /** Service-account client id in the target realm */
    private String clientId;

    /** Service-account client secret */
    private String clientSecret;

    /** Admin username in the master realm (fallback auth method) */
    private String username;

    /** Admin password in the master realm */
    private String password;
}


