package com.tokebak.EchoesOfOrbis.services.effects;

import javax.annotation.Nonnull;

/**
 * Represents a single upgrade option in the Vampire Survivors-style selection.
 * Either a boost to an existing effect or adding a new effect type.
 */
public abstract class UpgradeOption {

    @Nonnull
    public abstract WeaponEffectType getEffectType();

    /**
     * Boost an existing effect: increment its level.
     */
    public static final class BoostOption extends UpgradeOption {
        private final WeaponEffectType effectType;
        private final int currentLevel;

        public BoostOption(@Nonnull final WeaponEffectType effectType, final int currentLevel) {
            this.effectType = effectType;
            this.currentLevel = currentLevel;
        }

        @Override
        @Nonnull
        public WeaponEffectType getEffectType() {
            return this.effectType;
        }

        public int getCurrentLevel() {
            return this.currentLevel;
        }
    }

    /**
     * Add a new effect type (at level 1).
     */
    public static final class NewEffectOption extends UpgradeOption {
        private final WeaponEffectType effectType;

        public NewEffectOption(@Nonnull final WeaponEffectType effectType) {
            this.effectType = effectType;
        }

        @Override
        @Nonnull
        public WeaponEffectType getEffectType() {
            return this.effectType;
        }
    }
}
