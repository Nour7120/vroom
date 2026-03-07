package com.county_cars.vroom.modules.registration.service;

import com.county_cars.vroom.modules.registration.dto.RegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationResponse;
import com.county_cars.vroom.modules.registration.dto.ResendVerificationRequest;

public interface RegistrationService {

    /**
     * Full registration flow:
     * <ol>
     *   <li>Create user in Keycloak (with built-in VERIFY_EMAIL required action)</li>
     *   <li>Persist UserProfile in DB with status {@code PENDING_MAIL_VERIFICATION}</li>
     *   <li>On any DB error → delete the Keycloak user (rollback)</li>
     * </ol>
     */
    RegistrationResponse register(RegistrationRequest request);

    /**
     * Resend the Keycloak verification email.
     * Enforces:
     * <ul>
     *   <li>Minimum interval between sends (configurable, default 2 min)</li>
     *   <li>Daily maximum retries per email (configurable, default 5)</li>
     * </ul>
     *
     * @throws com.county_cars.vroom.common.exception.BadRequestException if cooldown or daily cap is active
     * @throws com.county_cars.vroom.common.exception.NotFoundException    if no profile exists for the email
     */
    void resendVerificationEmail(ResendVerificationRequest request);
}

