package com.payflow.common.tracing;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIdContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {}

    public static String get() {
        String id = MDC.get(MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    public static void set(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_KEY, correlationId);
        } else {
            MDC.put(MDC_KEY, UUID.randomUUID().toString());
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}

