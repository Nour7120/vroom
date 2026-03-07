package com.county_cars.vroom.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(description = "HTTP status code")
    private int status;

    @Schema(description = "Short error type")
    private String error;

    @Schema(description = "Human-readable message")
    private String message;

    @Schema(description = "Request path")
    private String path;

    @Schema(description = "Timestamp of the error")
    private LocalDateTime timestamp;

    @Schema(description = "Field-level validation errors (if any)")
    private List<FieldError> fieldErrors;

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}

