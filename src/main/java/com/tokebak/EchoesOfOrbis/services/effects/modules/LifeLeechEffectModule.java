package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.LifeLeechProcessor;
import javax.annotation.Nonnull;

/** Self-contained LIFE_LEECH: heal for % of damage dealt. 1% at level 1, +1% per boost, cap 15%. */
public class LifeLeechEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.01;
    private static final double VALUE_PER_LEVEL = 0.01;
    private static final double MAX_VALUE = 0.15;
    private static final int MAX_LEVEL = 10;
    private static final String DESCRIPTION_TEMPLATE = "Heal {value} of damage dealt";
    private static final String SHORT_DESCRIPTION = "Heal for a portion of damage dealt";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.LIFE_LEECH)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new LifeLeechProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.LIFE_LEECH;
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
