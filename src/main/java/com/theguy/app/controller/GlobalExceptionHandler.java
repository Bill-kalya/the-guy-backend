package com.theguy.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.theguy.app.dto.StructuredErrorResponse;
import com.theguy.app.enums.ErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StructuredErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<com.theguy.app.dto.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::mapFieldError)
            .collect(Collectors.toList());
        log.warn("Validation failed: {} errors", fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StructuredErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<com.theguy.app.dto.FieldError> fieldErrors = ex.getConstraintViolations().stream()
            .map(this::mapConstraintViolation)
            .collect(Collectors.toList());
        log.warn("Constraint violation: {} errors", fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StructuredErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request not readable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(ErrorCode.VALIDATION_FAILED, null));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<StructuredErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(buildStructuredResponse(ErrorCode.INVALID_CREDENTIALS, null));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<StructuredErrorResponse> handleLocked(LockedException ex) {
        log.warn("Account locked");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(buildStructuredResponse(ErrorCode.ACCOUNT_LOCKED, null));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<StructuredErrorResponse> handleDisabled(DisabledException ex) {
        log.warn("Account disabled/suspended");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(buildStructuredResponse(ErrorCode.ACCOUNT_SUSPENDED, null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<StructuredErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        List<com.theguy.app.dto.FieldError> fieldErrors = List.of(
            new com.theguy.app.dto.FieldError(ex.getParameterName(), "REQUIRED", "Missing required parameter")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StructuredErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter: {}", ex.getName());
        List<com.theguy.app.dto.FieldError> fieldErrors = List.of(
            new com.theguy.app.dto.FieldError(ex.getName(), "TYPE_MISMATCH", "Invalid value for parameter")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(ErrorCode.VALIDATION_FAILED, fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StructuredErrorResponse> handleIllegalArg(IllegalArgumentException ex) {
        String msg = ex.getMessage();
        log.warn("Illegal argument: {}", msg);
        ErrorCode code = resolveExceptionCode(msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(buildStructuredResponse(code, null));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<StructuredErrorResponse> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage();
        log.warn("Illegal state: {}", msg);
        ErrorCode code = resolveExceptionCode(msg);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(buildStructuredResponse(code, null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<StructuredErrorResponse> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage();
        log.error("Runtime exception: {}", msg, ex);
        ErrorCode code = resolveExceptionCode(msg);
        HttpStatus status = mapCodeToStatus(code);
        return ResponseEntity.status(status)
            .body(buildStructuredResponse(code, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StructuredErrorResponse> handleAll(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildStructuredResponse(ErrorCode.SERVER_ERROR, null));
    }

    private com.theguy.app.dto.FieldError mapFieldError(FieldError fe) {
        String errorCode = mapFieldToErrorCode(fe.getField(), fe.getCode());
        return new com.theguy.app.dto.FieldError(fe.getField(), errorCode, fe.getDefaultMessage());
    }

    private com.theguy.app.dto.FieldError mapConstraintViolation(ConstraintViolation<?> cv) {
        String field = extractFieldPath(cv);
        String errorCode = mapFieldToErrorCode(field, cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        return new com.theguy.app.dto.FieldError(field, errorCode, cv.getMessage());
    }

    private String extractFieldPath(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private String mapFieldToErrorCode(String field, String springCode) {
        if (springCode != null && springCode.contains("Pattern")) {
            return ErrorCode.PASSWORD_WEAK.getCode();
        }
        if (field != null && field.toLowerCase().contains("email")) {
            return ErrorCode.EMAIL_INVALID.getCode();
        }
        if (field != null && field.toLowerCase().contains("phone")) {
            return ErrorCode.PHONE_INVALID.getCode();
        }
        if (springCode != null && springCode.contains("Size")) {
            return ErrorCode.PASSWORD_WEAK.getCode();
        }
        return springCode;
    }

    private ErrorCode resolveExceptionCode(String message) {
        if (message == null) return ErrorCode.SERVER_ERROR;
        if (message.contains("Email already registered")) return ErrorCode.EMAIL_EXISTS;
        if (message.contains("User not found")) return ErrorCode.NOT_FOUND;
        if (message.toLowerCase().contains("provider")) return ErrorCode.PROVIDER_OFFLINE;
        if (message.contains("locked")) return ErrorCode.ACCOUNT_LOCKED;
        if (message.contains("disabled") || message.contains("suspended")) return ErrorCode.ACCOUNT_SUSPENDED;
        if (message.contains("OTP") || message.contains("otp") || message.contains("Verification code")) {
            return ErrorCode.OTP_INVALID;
        }
        return ErrorCode.SERVER_ERROR;
    }

    private HttpStatus mapCodeToStatus(ErrorCode code) {
        return switch (code) {
            case EMAIL_EXISTS -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ACCOUNT_LOCKED, ACCOUNT_SUSPENDED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private StructuredErrorResponse buildStructuredResponse(ErrorCode errorCode, List<com.theguy.app.dto.FieldError> errors) {
        return StructuredErrorResponse.builder()
            .success(false)
            .errorCode(errorCode.getCode())
            .message(errorCode.getMessage())
            .errors(errors)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build();
    }
}
