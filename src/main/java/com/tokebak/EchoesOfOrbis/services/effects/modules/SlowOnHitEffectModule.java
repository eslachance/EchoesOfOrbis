package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.SlowOnHitProcessor;
import javax.annotation.Nonnull;

/** Self-contained SLOW_ON_HIT: chance to slow. 5% at level 1, +2% per boost, cap 25%. */
public class SlowOnHitEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.02;
    private static final String DESCRIPTION_TEMPLATE = "{value} chance to slow on hit";
    private static final String SHORT_DESCRIPTION = "Chance to slow enemy movement";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.SLOW_ON_HIT)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new SlowOnHitProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.SLOW_ON_HIT;
    }

    @Override
    @Nonnull
    public WeaponEffectDefinition getDefinition() {
        return this.definition;
    }

    @Override
    @Nonnull
    public EffectProcessor getProcessor() {
        return this.processor;
    }

    @Override
    @Nonnull
    public String getShortDescription() {
        return SHORT_DESCRIPTION;
    }
}
