package com.theguy.app.utils;

import java.util.regex.Pattern;

public final class PhoneNormalizer {

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");

    private PhoneNormalizer() {
    }

    /** Normalizes a Kenyan phone number to E.164 form: 2547XXXXXXXX. Returns null if not parseable. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = NON_DIGITS.matcher(raw.trim()).replaceAll("");
        if (digits.isEmpty()) return null;

        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("254")) {
            digits = digits.length() == 12 ? digits : digits;
        } else if (digits.startsWith("0") && digits.length() == 10) {
            digits = "254" + digits.substring(1);
        } else if (digits.length() == 9 && digits.startsWith("7")) {
            digits = "254" + digits;
        } else if (digits.length() == 9 && digits.startsWith("1")) {
            digits = "254" + digits;
        }

        if (digits.length() == 12 && digits.startsWith("254")) {
            return digits;
        }
        return null;
    }
}
