package com.payflow.common.error;

import com.payflow.common.dto.ApiErrorResponse;
import com.payflow.common.tracing.CorrelationIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PayflowException.class)
    public ResponseEntity<ApiErrorResponse> handlePayflowException(PayflowException ex) {
        String correlationId = CorrelationIdContext.get();
        log.warn("Business error [correlationId={}]: code={}, message={}",
                correlationId, ex.getErrorCode().name(), ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.of(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.getDetails(),
                correlationId
        );
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = CorrelationIdContext.get();
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        log.warn("Validation failure [correlationId={}]: {}", correlationId, details);

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.VALIDATION_ERROR.name(),
                "Request validation failed",
                details,
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeaderException(MissingRequestHeaderException ex) {
        String correlationId = CorrelationIdContext.get();
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INVALID_REQUEST.name(),
                "Required header missing: " + ex.getHeaderName(),
                List.of(ex.getMessage()),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        String correlationId = CorrelationIdContext.get();
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INVALID_REQUEST.name(),
                "Malformed JSON request payload",
                List.of("The request body could not be parsed"),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        String correlationId = CorrelationIdContext.get();
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.FORBIDDEN.name(),
                "Access denied to resource",
                List.of(ex.getMessage()),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        String correlationId = CorrelationIdContext.get();
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.UNAUTHORIZED.name(),
                "Authentication failed",
                List.of(ex.getMessage()),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        String correlationId = CorrelationIdContext.get();
        log.warn("Static resource or endpoint not found [correlationId={}]: {}", correlationId, ex.getResourcePath());
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                "Resource or endpoint not found: /" + ex.getResourcePath(),
                List.of(ex.getMessage()),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        String correlationId = CorrelationIdContext.get();
        log.error("Unhandled internal server error [correlationId={}]", correlationId, ex);

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected internal error occurred. Please contact support with correlationId.",
                List.of(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

