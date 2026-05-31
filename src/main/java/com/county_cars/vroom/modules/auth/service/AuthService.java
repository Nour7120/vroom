package com.county_cars.vroom.modules.auth.service;

import com.county_cars.vroom.modules.auth.dto.ChangePasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordResponse;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordResponse;
import com.county_cars.vroom.modules.auth.dto.UserMeResponse;

public interface AuthService {

    UserMeResponse getMe();

    ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

    ChangePasswordResponse changePassword(ChangePasswordRequest request);
}

