package com.securetrade.escrow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Named points of the escrow protocol where the crash-test suite kills the server. Inactive unless
 * the system property {@code securetrade.debug.haltAt} names a point; it is read on every check, so
 * the dev harness can arm a point while the server runs. See Docs/TESTING.md.
 */
enum CrashPoint {
    AFTER_PREPARED,
    AFTER_OFFERS_SAVED,
    AFTER_COMMITTING,
    AFTER_FIRST_COMPLETION_SAVE,
    DURING_LOGIN_RECOVERY;

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String TARGET_PROPERTY = "securetrade.debug.haltAt";

    void reach() {
        if (name().equalsIgnoreCase(System.getProperty(TARGET_PROPERTY, ""))) {
            LOGGER.error("Secure Trade crash test: halting the JVM at {}", this);
            Runtime.getRuntime().halt(137);
        }
    }
}
