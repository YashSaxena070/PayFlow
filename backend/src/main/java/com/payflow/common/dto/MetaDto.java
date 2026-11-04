package com.payflow.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetaDto(
        String correlationId,
        Instant timestamp
) {}

