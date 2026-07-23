package com.theguy.app.enums;

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
}
