package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.StaminaRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_STAMINA: bonus max stamina from rings. Applied from bauble container.
 * Base +10 per level, +10 per boost, cap 50 (e.g. level 1 = 10, level 5 = 50).
 */
public class StaminaRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 10.0;
    private static final double VALUE_PER_LEVEL = 10.0;
    private static final double MAX_VALUE = 50.0;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Bonus max stamina";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_STAMINA)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} max stamina")
            .valueDisplayFormat(ValueDisplayFormat.RAW_NUMBER)
            .build();

    private final EffectProcessor processor = new StaminaRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_STAMINA;
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
