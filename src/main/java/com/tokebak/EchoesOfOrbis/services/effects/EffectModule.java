package com.tokebak.EchoesOfOrbis.services.effects;

import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import javax.annotation.Nonnull;

/**
 * Self-contained effect registration: definition, processor, and display strings.
 * Each effect module registers with the central WeaponEffectsService and provides
 * everything needed to run and display the effect.
 */
public interface EffectModule {

    /**
     * The effect type this module implements.
     */
    @Nonnull
    WeaponEffectType getType();

    /**
     * Definition (base value, per-level value, cap, description template, display format).
     * Built from this module's configuration.
     */
    @Nonnull
    WeaponEffectDefinition getDefinition();

    /**
     * Processor that applies the effect (e.g. on damage dealt).
     */
    @Nonnull
    EffectProcessor getProcessor();

    /**
     * Short description for UI (e.g. "Bonus damage as percentage of hit").
     */
    @Nonnull
    String getShortDescription();
}
