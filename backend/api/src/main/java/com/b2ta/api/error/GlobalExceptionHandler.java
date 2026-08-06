package com.b2ta.api.error;

import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.error.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates exceptions into the structured error envelope defined in the design document.
 *
 * <p>Only {@link ApiException} carries a message that is safe to show a user. Anything else is
 * reported as a generic 500 with a fixed message, and the detail goes to the log instead — an
 * unexpected exception message can contain a SQL fragment, a file path, or a fragment of student
 * text, none of which belongs in a response body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        if (e.getStatus() >= 500) {
            log.error("Request failed with {} {}: {}", e.getStatus(), e.getCode(), e.getMessage(), e);
        } else {
            log.debug("Request rejected with {} {}: {}", e.getStatus(), e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage(), e.getDetails()));
    }

    /** Bean validation on a {@code @RequestBody}: reported per field so the SPA can mark inputs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError ->
                fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(globalError ->
                fields.putIfAbsent(globalError.getObjectName(), globalError.getDefaultMessage()));
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, "Request validation failed", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, Object> fields = e.getConstraintViolations().stream().collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                violation -> (Object) violation.getMessage(),
                (first, second) -> first,
                LinkedHashMap::new));
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, "Request validation failed", fields));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        log.debug("Malformed request: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                ErrorCode.VALIDATION_FAILED, "Request body or parameters could not be read",
                Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                ErrorCode.NOT_FOUND, "Resource not found", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", Map.of()));
    }
}
