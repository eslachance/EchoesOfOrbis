package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;

import javax.annotation.Nonnull;

/**
 * Processor for RING_SIGNATURE_ENERGY. Application is in ItemExpDamageSystem when player deals damage (add bonus to SignatureEnergy).
 */
public class SignatureEnergyRingProcessor implements EffectProcessor {

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Bonus applied in ItemExpDamageSystem after damage is dealt
    }
}
