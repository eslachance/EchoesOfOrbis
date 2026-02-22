package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.ArmorFireResistanceProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;

import javax.annotation.Nonnull;

/**
 * ARMOR_FIRE_RESISTANCE: fire damage reduction. Applied as stat modifier from armor.
 */
public class ArmorFireResistanceEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final double MAX_VALUE = 0.25;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Fire resistance";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.ARMOR_FIRE_RESISTANCE)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} fire resistance")
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new ArmorFireResistanceProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.ARMOR_FIRE_RESISTANCE;
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
