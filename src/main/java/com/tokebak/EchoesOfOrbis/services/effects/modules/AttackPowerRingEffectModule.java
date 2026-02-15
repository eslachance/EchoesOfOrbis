package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.AttackPowerRingProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;

import javax.annotation.Nonnull;

/**
 * RING_ATTACK_POWER: bonus damage multiplier from rings. Applied in PlayerAttackPowerDamageSystem.
 * 5% per level, +5% per boost, cap 25% (e.g. level 1 = 0.05, level 5 = 0.25).
 */
public class AttackPowerRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;   // 5% at level 1
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final double MAX_VALUE = 0.25;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Bonus attack power (damage %)";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_ATTACK_POWER)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} attack power")
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new AttackPowerRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_ATTACK_POWER;
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
