package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.ArmorGeneralResistanceProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;

import javax.annotation.Nonnull;

/**
 * ARMOR_GENERAL_RESISTANCE: general damage reduction (all types). Applied as stat modifier from armor.
 */
public class ArmorGeneralResistanceEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final String SHORT_DESCRIPTION = "General resistance";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.ARMOR_GENERAL_RESISTANCE)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .description("+{value} general resistance")
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new ArmorGeneralResistanceProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.ARMOR_GENERAL_RESISTANCE;
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
