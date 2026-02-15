package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;

import javax.annotation.Nonnull;

/**
 * Processor for RING_HEALTH effect.
 * Actual application is in PlayerStatModifierService (sum effect values from bauble rings).
 */
public class HealthRingProcessor implements EffectProcessor {

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Ring effects are applied passively from bauble container, not on damage
    }
}
