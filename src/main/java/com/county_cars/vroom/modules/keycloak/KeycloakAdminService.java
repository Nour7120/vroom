package com.county_cars.vroom.modules.keycloak;

import com.county_cars.vroom.common.exception.ConflictException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.modules.keycloak.config.KeycloakAdminProperties;
import com.county_cars.vroom.modules.keycloak.dto.CreateKeycloakUserRequest;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central service for all Keycloak Admin REST API operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create a new user (Keycloak triggers its own verification email)</li>
 *   <li>Delete a user (rollback on registration failure)</li>
 *   <li>Trigger re-send of Keycloak's built-in verification email</li>
 *   <li>Trigger Keycloak's built-in "forgot password" / reset-password email</li>
 *   <li>Hard-reset a user's password directly (admin flow)</li>
 *   <li>Enable / disable a user account</li>
 *   <li>Update user attributes (firstName, lastName, email)</li>
 *   <li>Fetch a user by ID or email</li>
 * </ul>
 * </p>
 *
 * <p>All email sending (verification, reset-password, forgot-password) is delegated to
 * Keycloak's own email facilities — the backend never sends mail directly.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final Keycloak keycloak;
    private final KeycloakAdminProperties props;
    private final RestClient restClient;

    // ─── Create ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new Keycloak user and returns the assigned UUID.
     * If {@link CreateKeycloakUserRequest#isSendVerificationEmail()} is {@code true},
     * Keycloak will automatically dispatch its built-in email-verification message.
     *
     * @throws ConflictException if the email is already registered in Keycloak
     */
    public String createUser(CreateKeycloakUserRequest request) {
        UserRepresentation user = buildUserRepresentation(request);

        try (Response response = usersResource().create(user)) {
            int status = response.getStatus();
            log.info("Keycloak create-user response status={} for email={}", status, request.getEmail());

            if (status == 409) {
                throw new ConflictException(
                        "Email is already registered in Keycloak: " + request.getEmail());
            }
            if (status < 200 || status >= 300) {
                String body = response.hasEntity() ? response.readEntity(String.class) : "(no body)";
                throw new IllegalStateException(
                        "Keycloak user creation failed – status=" + status + " body=" + body);
            }

            // Extract the new user's UUID from the Location header
            String location = response.getLocation().toString();
            String userId = location.substring(location.lastIndexOf('/') + 1);
            log.info("Keycloak user created: id={} email={}", userId, request.getEmail());
            if (request.isSendVerificationEmail())
            {
                userResource(userId).executeActionsEmail(List.of("VERIFY_EMAIL"));
                log.info("Keycloak verification email sent for userId={}", userId);
            }
            return userId;
        }
    }

    // ─── Delete ──────────────────────────────────────────────────────────────────

    /**
     * Hard-deletes a user from Keycloak.
     * Used as a rollback step when DB persistence fails after Keycloak creation.
     */
    public void deleteUser(String keycloakUserId) {
        try {
            usersResource().delete(keycloakUserId);
            log.info("Keycloak user deleted: id={}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to delete Keycloak user id={}: {}", keycloakUserId, e.getMessage());
        }
    }

    // ─── Email actions (all delegated to Keycloak) ────────────────────────────

    /**
     * Triggers Keycloak to re-send the email-verification message.
     * Keycloak tracks its own resend rate; this call just instructs it to execute
     * the {@code VERIFY_EMAIL} required action.
     */
    public void sendVerificationEmail(String keycloakUserId) {
        log.info("Triggering Keycloak verification email for userId={}", keycloakUserId);
        userResource(keycloakUserId).executeActionsEmail(
                List.of("VERIFY_EMAIL"));
    }

    /**
     * Triggers Keycloak to send a "forgot password" / reset-password email.
     * The user receives a link to set a new password without knowing the old one.
     */
    public void sendPasswordResetEmail(String keycloakUserId) {
        log.info("Triggering Keycloak password-reset email for userId={}", keycloakUserId);
        userResource(keycloakUserId).executeActionsEmail(
                List.of("UPDATE_PASSWORD"));
    }

    /**
     * Triggers Keycloak to send a combined "forgot password + verify email" email.
     * Useful when a user has not verified their email and also needs to reset their password.
     */
    public void sendForgotPasswordEmail(String keycloakUserId) {
        log.info("Triggering Keycloak forgot-password email for userId={}", keycloakUserId);
        userResource(keycloakUserId).executeActionsEmail(
                List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
    }

    // ─── Password ────────────────────────────────────────────────────────────────

    /**
     * Directly resets a user's password (admin-forced, no email needed).
     *
     * @param keycloakUserId the Keycloak UUID
     * @param newPassword    the plain-text new password (Keycloak hashes it)
     * @param temporary      if {@code true} the user must change it on next login
     */
    public void resetPassword(String keycloakUserId, String newPassword, boolean temporary) {
        log.info("Admin resetting password for userId={} temporary={}", keycloakUserId, temporary);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(temporary);
        userResource(keycloakUserId).resetPassword(credential);
    }

    // ─── Enable / Disable ────────────────────────────────────────────────────────

    /**
     * Enables or disables a Keycloak account.
     * A disabled account cannot obtain tokens even with valid credentials.
     */
    public void setUserEnabled(String keycloakUserId, boolean enabled) {
        log.info("Setting Keycloak user id={} enabled={}", keycloakUserId, enabled);
        UserRepresentation user = getUserById(keycloakUserId);
        user.setEnabled(enabled);
        userResource(keycloakUserId).update(user);
    }

    // ─── Update ──────────────────────────────────────────────────────────────────

    /**
     * Updates firstName, lastName and/or email on a Keycloak user.
     * Pass {@code null} for any field that should not change.
     */
    public void updateUser(String keycloakUserId, String firstName, String lastName, String email) {
        log.info("Updating Keycloak user id={}", keycloakUserId);
        UserRepresentation user = getUserById(keycloakUserId);
        if (firstName != null) user.setFirstName(firstName);
        if (lastName  != null) user.setLastName(lastName);
        if (email     != null) user.setEmail(email);
        userResource(keycloakUserId).update(user);
    }

    // ─── Query ───────────────────────────────────────────────────────────────────

    /**
     * Fetches a Keycloak user by their UUID.
     *
     * @throws NotFoundException if no user with that id exists
     */
    public UserRepresentation getUserById(String keycloakUserId) {
        try {
            return userResource(keycloakUserId).toRepresentation();
        } catch (Exception e) {
            throw new NotFoundException("Keycloak user not found: " + keycloakUserId);
        }
    }

    /**
     * Searches Keycloak for a user by exact email match.
     *
     * @throws NotFoundException if no matching user exists
     */
    public UserRepresentation getUserByEmail(String email) {
        List<UserRepresentation> users = usersResource().searchByEmail(email, true);
        if (users == null || users.isEmpty()) {
            throw new NotFoundException("No Keycloak user found with email: " + email);
        }
        return users.getFirst();
    }

    // ─── Credential verification ─────────────────────────────────────────────────

    /**
     * Verifies a user's current password by attempting a Resource Owner Password
     * Credentials (ROPC) token grant against Keycloak's token endpoint.
     *
     * <p>This is the only reliable way to check "is this the correct password?"
     * without storing credentials server-side. The token is immediately discarded.</p>
     *
     * @param email    the user's email (used as Keycloak username)
     * @param password the password to verify
     * @return {@code true} if Keycloak accepted the credentials, {@code false} if 401
     */
    public boolean verifyUserCredentials(String email, String password) {
        String tokenUrl = props.getServerUrl()
                + "/realms/" + props.getRealm()
                + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("username", email);
        form.add("password", password);
        form.add("scope", "openid");

        try {
            restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Credential verification succeeded for email={}", email);
            return true;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.debug("Credential verification failed (wrong password) for email={}", email);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error during credential verification for email={}: {}", email, e.getMessage());
            throw new IllegalStateException("Could not verify credentials with Keycloak. Please try again.", e);
        }
    }

    // ─── Two-Factor Authentication (TOTP) ────────────────────────────────────────

    /**
     * Enables TOTP-based 2FA for the user by adding {@code "CONFIGURE_TOTP"} to
     * their required actions list (idempotent — does not duplicate the entry).
     *
     * <p>Keycloak will prompt the user to scan a QR code on next login
     * if they have not already configured an OTP device.</p>
     *
     * @throws IllegalStateException if the Keycloak Admin API call fails
     */
    public void enableTwoFactor(String keycloakUserId) {
        log.info("Enabling 2FA (CONFIGURE_TOTP) for keycloakUserId={}", keycloakUserId);
        try {
            UserResource resource = userResource(keycloakUserId);
            UserRepresentation user = resource.toRepresentation();

            List<String> actions = new java.util.ArrayList<>(
                    user.getRequiredActions() != null ? user.getRequiredActions() : List.of());

            if (!actions.contains("CONFIGURE_TOTP")) {
                actions.add("CONFIGURE_TOTP");
                user.setRequiredActions(actions);
                resource.update(user);
                log.info("CONFIGURE_TOTP added for keycloakUserId={}", keycloakUserId);
            } else {
                log.info("CONFIGURE_TOTP already present for keycloakUserId={} — no change", keycloakUserId);
            }
        } catch (Exception e) {
            log.error("Failed to enable 2FA for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            throw new IllegalStateException("Could not enable 2FA. Please try again.", e);
        }
    }

    /**
     * Disables TOTP-based 2FA for the user by:
     * <ol>
     *   <li>Deleting every credential whose type is {@code "otp"}</li>
     *   <li>Removing {@code "CONFIGURE_TOTP"} from the user's required actions list</li>
     * </ol>
     *
     * @throws IllegalStateException if the Keycloak Admin API call fails
     */
    public void disableTwoFactor(String keycloakUserId) {
        log.info("Disabling 2FA for keycloakUserId={}", keycloakUserId);
        try {
            UserResource resource = userResource(keycloakUserId);

            // ── Step 1: delete all OTP credentials ───────────────────────────────
            var credentials = resource.credentials();
            if (credentials != null) {
                credentials.stream()
                        .filter(c -> "otp".equalsIgnoreCase(c.getType()))
                        .forEach(c -> {
                            log.debug("Removing OTP credential id={} for keycloakUserId={}", c.getId(), keycloakUserId);
                            resource.removeCredential(c.getId());
                        });
            }

            // ── Step 2: remove CONFIGURE_TOTP from required actions ───────────────
            UserRepresentation user = resource.toRepresentation();
            List<String> actions = new java.util.ArrayList<>(
                    user.getRequiredActions() != null ? user.getRequiredActions() : List.of());

            if (actions.remove("CONFIGURE_TOTP")) {
                user.setRequiredActions(actions);
                resource.update(user);
                log.info("CONFIGURE_TOTP removed from required actions for keycloakUserId={}", keycloakUserId);
            }

            log.info("2FA disabled for keycloakUserId={}", keycloakUserId);
        } catch (Exception e) {
            log.error("Failed to disable 2FA for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            throw new IllegalStateException("Could not disable 2FA. Please try again.", e);
        }
    }

    /**
     * Returns whether TOTP-based 2FA is currently active for the user.
     *
     * <p>A user is considered to have 2FA enabled if at least one credential
     * with type {@code "otp"} exists in Keycloak (i.e. they have completed
     * the TOTP setup, not just been prompted to do so).</p>
     *
     * @return {@code true} if an OTP credential exists; {@code false} otherwise or on error
     */
    public boolean isTwoFactorEnabled(String keycloakUserId) {
        try {
            var credentials = userResource(keycloakUserId).credentials();
            if (credentials == null || credentials.isEmpty()) {
                return false;
            }
            return credentials.stream().anyMatch(c -> "otp".equalsIgnoreCase(c.getType()));
        } catch (Exception e) {
            log.error("Failed to check 2FA status for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            return false;
        }
    }

    // ─── Profile enrichment (minimal — /me endpoint only) ────────────────────────

    /**
     * Returns whether the user's email has been verified in Keycloak.
     *
     * <p>Uses a single {@code toRepresentation()} call and reads {@code emailVerified}.
     * No other fields are consumed from the response.</p>
     *
     * @return {@code true} if verified; {@code false} on any error (fail-safe)
     */
    public boolean isEmailVerified(String keycloakUserId) {
        try {
            Boolean verified = userResource(keycloakUserId).toRepresentation().isEmailVerified();
            return Boolean.TRUE.equals(verified);
        } catch (Exception e) {
            log.error("Failed to fetch emailVerified for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the set of federated identity provider aliases linked to this account
     * (e.g. {@code "google"}, {@code "apple"}).
     *
     * <p>Uses {@code getFederatedIdentity()} — a dedicated lightweight endpoint that does
     * NOT return full user attributes or roles.</p>
     *
     * @return provider alias set; empty set on any error (fail-safe)
     */
    public Set<String> getFederatedIdentityProviders(String keycloakUserId) {
        try {
            var federatedIdentities = userResource(keycloakUserId).getFederatedIdentity();
            if (federatedIdentities == null || federatedIdentities.isEmpty()) {
                return Set.of();
            }
            return federatedIdentities.stream()
                    .map(fi -> fi.getIdentityProvider())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception e) {
            log.error("Failed to fetch federated identities for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * Returns whether the user has a local password credential in Keycloak.
     *
     * <p>Uses {@code credentials()} and checks for any entry with type {@code "password"}.
     * Social-only accounts (created via an IdP) will return {@code false}.</p>
     *
     * @return {@code true} if a local password credential exists; {@code false} on any error (fail-safe)
     */
    public boolean hasLocalPassword(String keycloakUserId) {
        try {
            var credentials = userResource(keycloakUserId).credentials();
            if (credentials == null || credentials.isEmpty()) {
                return false;
            }
            return credentials.stream()
                    .anyMatch(c -> "password".equalsIgnoreCase(c.getType()));
        } catch (Exception e) {
            log.error("Failed to fetch credentials for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
            return false;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private UsersResource usersResource() {
        return keycloak.realm(props.getRealm()).users();
    }

    private UserResource userResource(String keycloakUserId) {
        return usersResource().get(keycloakUserId);
    }

    private UserRepresentation buildUserRepresentation(CreateKeycloakUserRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setEmail(request.getEmail());
        user.setUsername(request.getEmail());   // use email as username
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEnabled(request.isEnabled());
        user.setEmailVerified(false);

        // Required actions
        if (request.isSendVerificationEmail()) {
            user.setRequiredActions(List.of("VERIFY_EMAIL"));
        } else {
            user.setRequiredActions(Collections.emptyList());
        }

        // Initial password credential
        if (request.getPassword() != null) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(request.getPassword());
            credential.setTemporary(false);
            user.setCredentials(List.of(credential));
        }

        return user;
    }
}


