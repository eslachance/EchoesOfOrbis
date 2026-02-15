package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.ThornsRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_THORNS: when the player takes damage, reflect damage back at the attacker.
 * Applied in ThornsDamageSystem (incoming damage handler).
 */
public class ThornsRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 2.0;   // 2 thorns damage at level 1
    private static final double VALUE_PER_LEVEL = 2.0;
    private static final double MAX_VALUE = 10.0;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Thorns (reflect damage when hit)";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_THORNS)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} thorns damage")
            .valueDisplayFormat(ValueDisplayFormat.RAW_NUMBER)
            .build();

    private final EffectProcessor processor = new ThornsRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_THORNS;
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
