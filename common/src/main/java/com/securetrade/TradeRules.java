package com.securetrade;

import com.securetrade.platform.Services;

import java.util.List;

public final class TradeRules {
    private TradeRules() {
    }

    public static boolean isDimensionAllowed(String dimensionId) {
        List<String> allowed = Services.PLATFORM.getAllowedDimensions();
        List<String> blocked = Services.PLATFORM.getBlockedDimensions();

        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(dimensionId);
        }

        if (blocked != null && !blocked.isEmpty()) {
            return !blocked.contains(dimensionId);
        }

        return true;
    }
}
