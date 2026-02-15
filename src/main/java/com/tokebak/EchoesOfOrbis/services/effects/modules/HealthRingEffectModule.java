package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.HealthRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_HEALTH: bonus max health from rings. Applied from bauble container.
 * +25 per level, +25 per boost, cap 100 (e.g. level 1 = 25, level 4 = 100).
 */
public class HealthRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 25.0;
    private static final double VALUE_PER_LEVEL = 25.0;
    private static final double MAX_VALUE = 100.0;
    private static final int MAX_LEVEL = 4;
    private static final String SHORT_DESCRIPTION = "Bonus max health";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_HEALTH)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} max health")
            .valueDisplayFormat(ValueDisplayFormat.RAW_NUMBER)
            .build();

    private final EffectProcessor processor = new HealthRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_HEALTH;
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
