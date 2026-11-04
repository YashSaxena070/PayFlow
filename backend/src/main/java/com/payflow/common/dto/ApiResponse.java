package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        MetaDto meta
) {
    public static <T> ApiResponse<T> of(T data, String correlationId) {
        return new ApiResponse<>(data, new MetaDto(correlationId, Instant.now()));
    }
}

