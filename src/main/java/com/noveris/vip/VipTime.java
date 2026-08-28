package com.noveris.vip;

final class VipTime {
    static final long DAY_MS = 86_400_000L;
    private VipTime() {}
    static long expiryAfterDays(long now, int days) { return now + days * DAY_MS; }
    static long remainingDays(long now, long expiry) {
        if (expiry <= now) return 0;
        return (expiry - now + DAY_MS - 1) / DAY_MS;
    }
    static boolean expired(long now, long expiry) { return expiry <= now; }
}
