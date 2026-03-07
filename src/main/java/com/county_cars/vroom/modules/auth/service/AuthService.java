package com.county_cars.vroom.modules.auth.service;

import com.county_cars.vroom.modules.auth.dto.ChangePasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordResponse;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordResponse;
import com.county_cars.vroom.modules.auth.dto.UserMeResponse;

public interface AuthService {

    /**
     * Returns the full profile of the currently authenticated user,
     * enriched with minimal Keycloak data (emailVerified, authProviders, hasLocalPassword).
     *
     * <p>One DB query + up to 3 Keycloak admin calls. Keycloak failures are non-fatal
     * (fail-safe defaults are used so the endpoint never breaks).</p>
     *
     * @throws com.county_cars.vroom.common.exception.NotFoundException if the caller has no DB profile
     */
    UserMeResponse getMe();

    /**
     * Triggers a Keycloak password-reset email for the account associated with the given email.
     *
     * <p>The response is always a generic success message to prevent user enumeration.
     * Rate limits (configurable):
     * <ul>
     *   <li>Minimum interval between consecutive requests (default 2 min)</li>
     *   <li>Daily maximum attempts per email (default 5)</li>
     * </ul>
     *
     * @throws com.county_cars.vroom.common.exception.BadRequestException if rate limit is exceeded
     */
    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    /**
     * Changes the password for the currently authenticated user.
     *
     * <p>Flow:
     * <ol>
     *   <li>Resolve the caller's email from the JWT via {@code CurrentUserService}</li>
     *   <li>Load the {@code UserProfile} and verify the account is ACTIVE</li>
     *   <li>Verify the current password is correct by attempting a Keycloak token grant</li>
     *   <li>Validate newPassword == confirmNewPassword</li>
     *   <li>Call Keycloak Admin API to set the new password</li>
     * </ol>
     *
     * @throws com.county_cars.vroom.common.exception.BadRequestException    if passwords do not match or current password is wrong
     * @throws com.county_cars.vroom.common.exception.UnauthorizedException  if the account is not in ACTIVE state
     * @throws com.county_cars.vroom.common.exception.NotFoundException      if no profile found for the caller
     */
    ChangePasswordResponse changePassword(ChangePasswordRequest request);
}

