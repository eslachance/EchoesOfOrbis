package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.SignatureEnergyRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_SIGNATURE_ENERGY: +1 extra signature energy per level added on every attack.
 * Applied in ItemExpDamageSystem when the player deals damage.
 */
public class SignatureEnergyRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 1.0;   // +1 at level 1
    private static final double VALUE_PER_LEVEL = 1.0;
    private static final double MAX_VALUE = 5.0;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Signature energy boost (+1 per level per attack)";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_SIGNATURE_ENERGY)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} signature energy per attack")
            .valueDisplayFormat(ValueDisplayFormat.RAW_NUMBER)
            .build();

    private final EffectProcessor processor = new SignatureEnergyRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_SIGNATURE_ENERGY;
    }

    @Override
    @Nonnull
    public WeaponEffectDefinition getDefinition() {
        return definition;
    }

    @Override
    @Nonnull
    public EffectProcessor getProcessor() {
        return processor;
    }

    @Override
    @Nonnull
    public String getShortDescription() {
        return SHORT_DESCRIPTION;
    }
}
