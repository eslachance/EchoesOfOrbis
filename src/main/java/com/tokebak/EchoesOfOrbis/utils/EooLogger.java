package com.tokebak.EchoesOfOrbis.utils;

import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;

import javax.annotation.Nonnull;

/**
 * Centralized logging for the Echoes of Orbis mod.
 * Debug messages are only printed when {@code debug=true} in the config.
 * Info and warn messages are always printed.
 */
public final class EooLogger {

    private static final String PREFIX = "[EOO]";
    private static final String DEBUG_PREFIX = "[EOO:DEBUG]";
    private static final String WARN_PREFIX = "[EOO:WARN]";

    private static EchoesOfOrbisConfig config;

    private EooLogger() {
    }

    public static void init(@Nonnull final EchoesOfOrbisConfig cfg) {
        config = cfg;
    }

    public static boolean isDebug() {
        return config != null && config.isDebug();
    }

    public static void debug(@Nonnull final String message) {
        if (isDebug()) {
            System.out.println(DEBUG_PREFIX + " " + message);
        }
    }

    public static void debug(@Nonnull final String format, @Nonnull final Object... args) {
        if (isDebug()) {
            System.out.println(DEBUG_PREFIX + " " + String.format(format, args));
        }
    }

    public static void info(@Nonnull final String message) {
        System.out.println(PREFIX + " " + message);
    }

    public static void info(@Nonnull final String format, @Nonnull final Object... args) {
        System.out.println(PREFIX + " " + String.format(format, args));
    }

    public static void warn(@Nonnull final String message) {
        System.out.println(WARN_PREFIX + " " + message);
    }

    public static void warn(@Nonnull final String format, @Nonnull final Object... args) {
        System.out.println(WARN_PREFIX + " " + String.format(format, args));
    }
}
