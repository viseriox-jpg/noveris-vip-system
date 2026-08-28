package com.noveris.vip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VipTimeTest {
    @Test void calculatesThirtyRealDays() {
        assertEquals(30L * VipTime.DAY_MS, VipTime.expiryAfterDays(0, 30));
    }
    @Test void roundsRemainingPartialDayUp() {
        assertEquals(1, VipTime.remainingDays(1, VipTime.DAY_MS));
    }
    @Test void reachesZeroAtExpiry() {
        assertEquals(0, VipTime.remainingDays(1000, 1000));
        assertTrue(VipTime.expired(1000, 1000));
    }
    @Test void activeItemIsNotExpired() {
        assertFalse(VipTime.expired(999, 1000));
    }
}
