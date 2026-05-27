package com.securetrade.platform;

import java.util.ServiceLoader;

public class Services {
    public static final IPlatformHelper PLATFORM = ServiceLoader.load(IPlatformHelper.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Failed to load IPlatformHelper service"));
}
