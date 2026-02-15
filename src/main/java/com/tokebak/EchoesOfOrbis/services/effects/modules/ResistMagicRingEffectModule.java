package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.ResistMagicRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_RESIST_MAGIC: resist magic (damage reduction vs magic). Applied as stat modifier from rings.
 */
public class ResistMagicRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;   // 5% at level 1
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final double MAX_VALUE = 0.25;
    private static final int MAX_LEVEL = 5;
    private static final String SHORT_DESCRIPTION = "Resist magic";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_RESIST_MAGIC)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} resist magic")
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new ResistMagicRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_RESIST_MAGIC;
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
