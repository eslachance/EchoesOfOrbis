package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;

import javax.annotation.Nonnull;

/**
 * Processor for RING_ATTACK_POWER effect.
 * Actual application is in PlayerAttackPowerDamageSystem (multiply damage by 1 + sum of effect values).
 */
public class AttackPowerRingProcessor implements EffectProcessor {

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Ring attack power is applied in PlayerAttackPowerDamageSystem from bauble container
    }
}
