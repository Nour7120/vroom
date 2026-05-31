package com.county_cars.vroom.modules.registration.service;

import com.county_cars.vroom.modules.registration.dto.CompleteRegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationResponse;
import com.county_cars.vroom.modules.registration.dto.ResendVerificationRequest;

public interface RegistrationService {

    RegistrationResponse register(RegistrationRequest request);

    RegistrationResponse completeThirdPartyRegistration(CompleteRegistrationRequest request);

    void resendVerificationEmail(ResendVerificationRequest request);

}

