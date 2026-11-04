package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        ErrorDetail error,
        MetaDto meta
) {
    public record ErrorDetail(
            String code,
            String message,
            List<String> details
    ) {}

    public static ApiErrorResponse of(String code, String message, List<String> details, String correlationId) {
        return new ApiErrorResponse(
                new ErrorDetail(code, message, details != null ? details : Collections.emptyList()),
                new MetaDto(correlationId, java.time.Instant.now())
        );
    }
}

