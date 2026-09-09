package com.aibrowser.knowledgehub;

public final class Freshness {
    private Freshness() {}

    public static String state(long lastCheckedMillis, long nowMillis) {
        if (lastCheckedMillis <= 0) return "NEVER CHECKED";
        long ageHours = Math.max(0, nowMillis - lastCheckedMillis) / 3_600_000L;
        if (ageHours <= 24) return "FRESH";
        if (ageHours <= 24L * 7L) return "UPDATE";
        return "STALE";
    }
}
