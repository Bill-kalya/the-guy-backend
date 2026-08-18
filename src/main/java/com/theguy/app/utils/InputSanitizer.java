package com.theguy.app.utils;

public final class InputSanitizer {

    private InputSanitizer() {}

    public static String stripHtml(String input) {
        if (input == null) return null;
        return input
            .replaceAll("<[^>]*>", "")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&amp;", "&")
            .replaceAll("&quot;", "\"")
            .replaceAll("&#x27;", "'")
            .replaceAll("&#39;", "'")
            .trim();
    }

    public static String encodeForHtml(String input) {
        if (input == null) return null;
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }

    public static String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim().toLowerCase();
        if (trimmed.startsWith("javascript:") || trimmed.startsWith("data:") || trimmed.startsWith("vbscript:")) {
            return null;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null;
        }
        return url.trim();
    }
}
