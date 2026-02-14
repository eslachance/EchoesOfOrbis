package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DurabilitySaveProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import javax.annotation.Nonnull;

/** Self-contained DURABILITY_SAVE: chance to not lose durability. 5% at level 1, +5% per boost, cap 50%. */
public class DurabilitySaveEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final double MAX_VALUE = 0.50;
    private static final int MAX_LEVEL = 10;
    private static final String DESCRIPTION_TEMPLATE = "{value} chance to save durability";
    private static final String SHORT_DESCRIPTION = "Chance to not lose durability when hitting";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.DURABILITY_SAVE)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new DurabilitySaveProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.DURABILITY_SAVE;
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
