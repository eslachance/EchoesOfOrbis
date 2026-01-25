package com.tokebak.EchoesOfOrbis.services.effects;

import javax.annotation.Nonnull;

/**
 * Global definition for how an effect type behaves.
 * This defines the scaling formula and constraints for an effect.
 * 
 * These are configured globally (in config or code) and define
 * how effect instances on weapons calculate their values.
 */
public class WeaponEffectDefinition {
    
    private final WeaponEffectType type;
    
    /**
     * Value at effect level 1.
     */
    private final double baseValue;
    
    /**
     * Additional value gained per effect level after level 1.
     * Total value = baseValue + (effectLevel - 1) * valuePerLevel
     */
    private final double valuePerLevel;
    
    /**
     * Maximum value this effect can reach (0 = no cap).
     */
    private final double maxValue;
    
    /**
     * Maximum level this effect can be upgraded to.
     */
    private final int maxLevel;
    
    /**
     * For proc effects: chance to trigger (0.0 to 1.0).
     */
    private final double procChance;
    
    /**
     * For DoT/buff effects: duration in seconds.
     */
    private final double duration;
    
    /**
     * Human-readable description template.
     * Use {value} as placeholder for the calculated value.
     */
    private final String description;
    
    private WeaponEffectDefinition(final Builder builder) {
        this.type = builder.type;
        this.baseValue = builder.baseValue;
        this.valuePerLevel = builder.valuePerLevel;
        this.maxValue = builder.maxValue;
        this.maxLevel = builder.maxLevel;
        this.procChance = builder.procChance;
        this.duration = builder.duration;
        this.description = builder.description;
    }
    
    /**
     * Calculate the effect value for a given effect level.
     * @param effectLevel The level of the effect on the weapon
     * @return The calculated value
     */
    public double calculateValue(final int effectLevel) {
        if (effectLevel < 1) {
            return 0.0;
        }
        
        double value = this.baseValue + (effectLevel - 1) * this.valuePerLevel;
        
        if (this.maxValue > 0 && value > this.maxValue) {
            value = this.maxValue;
        }
        
        return value;
    }
    
    /**
     * Get a formatted description with the actual value.
     */
    @Nonnull
    public String getFormattedDescription(final int effectLevel) {
        final double value = this.calculateValue(effectLevel);
        // Format as percentage if value is small (< 1), otherwise as integer
        final String valueStr = value < 1.0 
                ? String.format("%.0f%%", value * 100) 
                : String.format("%.1f", value);
        return this.description.replace("{value}", valueStr);
    }
    
    // Getters
    
    public WeaponEffectType getType() {
        return this.type;
    }
    
    public double getBaseValue() {
        return this.baseValue;
    }
    
    public double getValuePerLevel() {
        return this.valuePerLevel;
    }
    
    public double getMaxValue() {
        return this.maxValue;
    }
    
    public int getMaxLevel() {
        return this.maxLevel;
    }
    
    public double getProcChance() {
        return this.procChance;
    }
    
    public double getDuration() {
        return this.duration;
    }
    
    public String getDescription() {
        return this.description;
    }
    
    /**
     * Create a new builder for this definition.
     */
    public static Builder builder(@Nonnull final WeaponEffectType type) {
        return new Builder(type);
    }
    
    /**
     * Builder for WeaponEffectDefinition.
     */
    public static class Builder {
        private final WeaponEffectType type;
        private double baseValue = 0.0;
        private double valuePerLevel = 0.0;
        private double maxValue = 0.0;
        private int maxLevel = 10;
        private double procChance = 1.0;
        private double duration = 0.0;
        private String description = "";
        
        private Builder(@Nonnull final WeaponEffectType type) {
            this.type = type;
        }
        
        public Builder baseValue(final double baseValue) {
            this.baseValue = baseValue;
            return this;
        }
        
        public Builder valuePerLevel(final double valuePerLevel) {
            this.valuePerLevel = valuePerLevel;
            return this;
        }
        
        public Builder maxValue(final double maxValue) {
            this.maxValue = maxValue;
            return this;
        }
        
        public Builder maxLevel(final int maxLevel) {
            this.maxLevel = maxLevel;
            return this;
        }
        
        public Builder procChance(final double procChance) {
            this.procChance = procChance;
            return this;
        }
        
        public Builder duration(final double duration) {
            this.duration = duration;
            return this;
        }
        
        public Builder description(final String description) {
            this.description = description;
            return this;
        }
        
        public WeaponEffectDefinition build() {
            return new WeaponEffectDefinition(this);
        }
    }
}
