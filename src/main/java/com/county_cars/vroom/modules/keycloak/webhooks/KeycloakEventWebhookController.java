package com.county_cars.vroom.modules.keycloak.webhooks;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal/keycloak/events")
@RequiredArgsConstructor
@Tag(name = "Keycloak Webhook", description = "Internal endpoint for Keycloak event callbacks")
public class KeycloakEventWebhookController {

    private final KeycloakEventWebhookService webhookService;
    String webhookSecret = "abc123";

    @Operation(summary = "Receive Keycloak events", description = "Internal webhook called by Keycloak on user events")
    @PostMapping
    public ResponseEntity<Void> handleEvent(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody KeycloakEventPayload payload) {
        log.info("Received Keycloak event: type={}, userId={}", payload.getType(), payload.getUserId());
        if (!webhookSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.handleEvent(payload);
        return ResponseEntity.ok().build();
    }

}
