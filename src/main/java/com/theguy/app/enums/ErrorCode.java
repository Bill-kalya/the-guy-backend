package com.theguy.app.enums;

public enum ErrorCode {
    // Auth
    EMAIL_EXISTS("EMAIL_EXISTS", "An account with this email already exists"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "Email or password is incorrect"),
    ACCOUNT_LOCKED("ACCOUNT_LOCKED", "Account is locked. Please contact support"),
    ACCOUNT_SUSPENDED("ACCOUNT_SUSPENDED", "Account has been suspended"),
    OTP_EXPIRED("OTP_EXPIRED", "Verification code has expired"),
    OTP_INVALID("OTP_INVALID", "Invalid verification code"),
    EMAIL_NOT_VERIFIED("EMAIL_NOT_VERIFIED", "Please verify your email first"),
    
    // Validation
    VALIDATION_FAILED("VALIDATION_FAILED", "Please check your input"),
    PASSWORD_WEAK("PASSWORD_WEAK", "Password does not meet requirements"),
    EMAIL_INVALID("EMAIL_INVALID", "Please enter a valid email address"),
    PHONE_INVALID("PHONE_INVALID", "Please enter a valid phone number"),
    
    // Provider
    PROVIDER_OFFLINE("PROVIDER_OFFLINE", "This provider is currently unavailable"),
    PROVIDER_BUSY("PROVIDER_BUSY", "This provider is currently busy"),
    VERIFICATION_REQUIRED("VERIFICATION_REQUIRED", "Please complete verification first"),
    
    // Booking
    BOOKING_CONFLICT("BOOKING_CONFLICT", "This time slot is no longer available"),
    JOB_NOT_FOUND("JOB_NOT_FOUND", "Job not found"),
    
    // Payment
    PAYMENT_FAILED("PAYMENT_FAILED", "Payment could not be completed"),
    INSUFFICIENT_FUNDS("INSUFFICIENT_FUNDS", "Insufficient balance"),
    PAYMENT_CANCELLED("PAYMENT_CANCELLED", "Payment was cancelled"),
    PAYMENT_TIMEOUT("PAYMENT_TIMEOUT", "Payment timed out. Please try again"),
    
    // General
    NETWORK_ERROR("NETWORK_ERROR", "Network error. Check your connection"),
    SERVER_ERROR("SERVER_ERROR", "Something went wrong. Please try again"),
    RATE_LIMITED("RATE_LIMITED", "Too many requests. Please wait a moment"),
    NOT_FOUND("NOT_FOUND", "Resource not found"),
    UNAUTHORIZED("UNAUTHORIZED", "Please log in to continue"),
    FORBIDDEN("FORBIDDEN", "You don't have permission for this action");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
