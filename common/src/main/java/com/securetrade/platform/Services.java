package com.securetrade.platform;

import java.util.ServiceLoader;

public class Services {
    public static final IPlatformHelper PLATFORM = loadPlatform();

    private static IPlatformHelper loadPlatform() {
        ServiceLoader<IPlatformHelper> loader = ServiceLoader.load(IPlatformHelper.class);
        for (IPlatformHelper helper : loader) {
            return helper;
        }
        throw new IllegalStateException("Failed to load IPlatformHelper service");
    }
}
