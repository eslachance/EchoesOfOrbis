package com.tokebak.EchoesOfOrbis.utils;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;

/**
 * Resolves translated strings for Echoes of Orbis from server.lang (eoo.* keys).
 * Code uses full keys like "server.eoo.ui.noPlayer"; the lang file has "eoo.ui.noPlayer = ...".
 */
public final class EOOTranslations {

    private static final String PREFIX_UI = "server.eoo.ui.";
    private static final String PREFIX_EFFECTS = "server.eoo.effects.";

    private EOOTranslations() {}

    /**
     * Get translated string for a key, or fallback if translation is missing.
     */
    @Nonnull
    public static String get(@Nonnull final String fullKey, @Nonnull final String fallback) {
        final String translated = Message.translation(fullKey).getAnsiMessage();
        if (translated != null && !translated.isEmpty() && !translated.equals(fullKey)) {
            return translated;
        }
        return fallback;
    }

    @Nonnull
    public static String ui(@Nonnull final String key, @Nonnull final String fallback) {
        return get(PREFIX_UI + key, fallback);
    }

    @Nonnull
    public static String ui(@Nonnull final String key, @Nonnull final String paramName, final Object paramValue, @Nonnull final String fallback) {
        final String translated = Message.translation(PREFIX_UI + key).param(paramName, String.valueOf(paramValue)).getAnsiMessage();
        if (translated != null && !translated.isEmpty() && !translated.equals(PREFIX_UI + key)) {
            return translated;
        }
        return fallback;
    }

    /**
     * Get effect display name from eoo.effects.<id>.name, or fallback (e.g. formatted id).
     */
    @Nonnull
    public static String effectName(@Nonnull final String effectId, @Nonnull final String fallback) {
        return get(PREFIX_EFFECTS + effectId + ".name", fallback);
    }

    /**
     * Get effect description template from eoo.effects.<id>.description (may contain {value}), or fallback.
     */
    @Nonnull
    public static String effectDescription(@Nonnull final String effectId, @Nonnull final String fallback) {
        return get(PREFIX_EFFECTS + effectId + ".description", fallback);
    }
}
