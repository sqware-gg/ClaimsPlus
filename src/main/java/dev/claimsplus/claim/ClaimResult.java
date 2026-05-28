package dev.claimsplus.claim;

import java.util.Map;

public record ClaimResult(boolean success, String messageKey, Map<String, String> placeholders) {
    public static ClaimResult success(String messageKey, Map<String, String> placeholders) {
        return new ClaimResult(true, messageKey, placeholders);
    }

    public static ClaimResult failure(String messageKey, Map<String, String> placeholders) {
        return new ClaimResult(false, messageKey, placeholders);
    }
}
