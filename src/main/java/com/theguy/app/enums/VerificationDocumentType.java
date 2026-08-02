package com.theguy.app.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum VerificationDocumentType {
    NATIONAL_ID("National ID"),
    BUSINESS_PERMIT("Business Permit"),
    PROFESSIONAL_LICENSE("Professional License"),
    KRA_PIN("KRA PIN");

    private final String displayName;

    VerificationDocumentType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static VerificationDocumentType fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(' ', '_');
        for (VerificationDocumentType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown document type: " + value);
    }
}
