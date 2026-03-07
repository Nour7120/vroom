package com.county_cars.vroom.modules.keycloak.webhooks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakEventPayload {

    private String type;          // e.g. "VERIFY_EMAIL", "LOGIN", "REGISTER"
    private String userId;        // Keycloak user ID
    private String realmId;
    private String clientId;
    private long time;
}
