package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DamagePercentProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import javax.annotation.Nonnull;

/**
 * Self-contained DAMAGE_PERCENT effect: bonus damage as percentage of hit.
 * 3% at level 1, +2% per boost, cap 25%.
 */
public class DamagePercentEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.03;       // 3% at effect level 1
    private static final double VALUE_PER_LEVEL = 0.02;  // +2% per boost
    private static final String DESCRIPTION_TEMPLATE = "+{value} damage";
    private static final String SHORT_DESCRIPTION = "Bonus damage as percentage of hit";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.DAMAGE_PERCENT)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new DamagePercentProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.DAMAGE_PERCENT;
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
