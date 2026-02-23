package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.FireOnHitProcessor;
import javax.annotation.Nonnull;

/** Self-contained FIRE_ON_HIT: chance to burn. 5% at level 1, +2% per boost, cap 25%. */
public class FireOnHitEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.02;
    private static final String DESCRIPTION_TEMPLATE = "{value} chance to burn on hit";
    private static final String SHORT_DESCRIPTION = "Chance to set enemies on fire";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.FIRE_ON_HIT)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new FireOnHitProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.FIRE_ON_HIT;
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
