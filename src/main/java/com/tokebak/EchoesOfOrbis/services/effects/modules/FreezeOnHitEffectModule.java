package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.FreezeOnHitProcessor;
import javax.annotation.Nonnull;

/** Self-contained FREEZE_ON_HIT: chance to freeze. 2% at level 1, +1% per boost, cap 12%. */
public class FreezeOnHitEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.02;
    private static final double VALUE_PER_LEVEL = 0.01;
    private static final double MAX_VALUE = 0.12;
    private static final int MAX_LEVEL = 10;
    private static final String DESCRIPTION_TEMPLATE = "{value} chance to freeze on hit";
    private static final String SHORT_DESCRIPTION = "Chance to freeze enemies in place";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.FREEZE_ON_HIT)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new FreezeOnHitProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.FREEZE_ON_HIT;
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
