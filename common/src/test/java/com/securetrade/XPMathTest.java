package com.securetrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XPMathTest {
    @Test
    void usesVanillaExperienceBoundaries() {
        assertEquals(0L, XPMath.getXpForLevels(0));
        assertEquals(352L, XPMath.getXpForLevels(16));
        assertEquals(394L, XPMath.getXpForLevels(17));
        assertEquals(1507L, XPMath.getXpForLevels(31));
        assertEquals(1628L, XPMath.getXpForLevels(32));
    }

    @Test
    void roundTripsLargeLevels() {
        for (int level : new int[] {1, 16, 17, 31, 32, 32_000, 1_000_000}) {
            long xp = XPMath.getXpForLevels(level);
            assertEquals(level, XPMath.getLevelForXp(xp));
            assertEquals(level - 1, XPMath.getLevelForXp(xp - 1));
        }
    }

    @Test
    void handlesLongExperienceWithoutOverflowingNegative() {
        int level = XPMath.getLevelForXp(Long.MAX_VALUE);
        assertTrue(level > 1_000_000);
        assertTrue(XPMath.getXpForLevels(level) >= 0L);
    }
}
