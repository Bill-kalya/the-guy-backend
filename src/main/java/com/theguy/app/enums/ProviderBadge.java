package com.theguy.app.enums;

/**
 * Reputation badge derived from a provider's Service Quality Score (SQS).
 * SQS influences visibility (ranking, badges, job priority) — never pricing.
 */
public enum ProviderBadge {
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold"),
    PLATINUM("Platinum");

    private final String displayName;

    ProviderBadge(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ProviderBadge fromScore(double score) {
        if (score >= 91) return PLATINUM;
        if (score >= 81) return GOLD;
        if (score >= 61) return SILVER;
        return BRONZE;
    }
}
