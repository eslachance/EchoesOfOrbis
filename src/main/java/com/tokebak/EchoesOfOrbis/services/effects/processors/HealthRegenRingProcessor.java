package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;

import javax.annotation.Nonnull;

/**
 * Processor for RING_HEALTH_REGEN. Application is in PlayerStatModifierService (modifier on regen stat from bauble rings).
 */
public class HealthRegenRingProcessor implements EffectProcessor {

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Ring effect applied passively from bauble container
    }
}
