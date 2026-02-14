package com.tokebak.EchoesOfOrbis.services.effects;

import javax.annotation.Nonnull;

/**
 * How an effect's numeric value is displayed in the UI.
 * This ensures display always matches how the processor uses the value.
 */
public enum ValueDisplayFormat {

    /**
     * Value is a fraction 0–1 (e.g. 0.5 = 50%). Display as percentage.
     * Used for: damage percent, damage flat (%), life leech, durability save, all proc chances.
     */
    PERCENT,

    /**
     * Value is a raw number (e.g. 2.5). Display as-is with one decimal.
     * Used for: flat amounts, multipliers &gt; 1, etc.
     */
    RAW_NUMBER,

    /**
     * Value is duration in seconds. Display as "X.Xs".
     */
    DURATION_SECONDS;

    /**
     * Format the value for display. Same semantics as used by effect processors.
     */
    @Nonnull
    public String format(final double value) {
        switch (this) {
            case PERCENT:
                return String.format("%.0f%%", value * 100);
            case RAW_NUMBER:
                return value >= 10 && value == Math.floor(value)
                        ? String.format("%.0f", value)
                        : String.format("%.1f", value);
            case DURATION_SECONDS:
                return String.format("%.1fs", value);
            default:
                return String.valueOf(value);
        }
    }
}
