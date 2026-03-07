package com.county_cars.vroom.modules.keycloak;

import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service to resolve the currently authenticated user's details from SecurityContextHolder.
 * Can be injected anywhere in the application to retrieve the logged-in user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Returns the Keycloak UUID (sub claim) of the current user.
     */
    public String getCurrentKeycloakUserId() {
        Jwt jwt = resolveJwt();
        return jwt.getSubject();
    }

    /**
     * Returns the email claim of the current user.
     */
    public String getCurrentEmail() {
        Jwt jwt = resolveJwt();
        return jwt.getClaimAsString("email");
    }

    /**
     * Loads the UserProfile entity from the database for the current user.
     */
    public UserProfile getCurrentUserProfile() {
        String keycloakId = getCurrentKeycloakUserId();
        return userProfileRepository.findByKeycloakUserId(keycloakId)
                .orElseThrow(() -> new UnauthorizedException(
                        "No user profile found for Keycloak user id: " + keycloakId));
    }

    /**
     * Returns the current user's database ID.
     */
    public Long getCurrentUserProfileId() {
        return getCurrentUserProfile().getId();
    }

    // ----------------------------------------------------------------

    private Jwt resolveJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            return jwtToken.getToken();
        }
        throw new UnauthorizedException("No JWT authentication found in security context");
    }
}

