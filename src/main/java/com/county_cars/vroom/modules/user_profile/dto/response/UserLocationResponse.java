package com.county_cars.vroom.modules.user_profile.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "User location response")
public class UserLocationResponse {
    private Long id;
    private Long userProfileId;
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}

