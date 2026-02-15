package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.HealthRegenRingProcessor;

import javax.annotation.Nonnull;

/**
 * RING_HEALTH_REGEN: health regen (same idea as food effect Health Regen I/II/III).
 * Three tiers only: T1, T2, T3 (not upgradeable past T3; shows as MAX in UI).
 * Applied via EntityEffect (HealthRegen_Buff_T1/T2/T3) when ring is in bauble.
 */
public class HealthRegenRingEffectModule implements EffectModule {

    private static final double BASE_VALUE = 1.0;   // T1
    private static final double VALUE_PER_LEVEL = 0.5; // T2 = 1.5, T3 = 2.0
    private static final double MAX_VALUE = 2.0;    // T3 matches food Health Regen III
    private static final int MAX_LEVEL = 3;        // T1, T2, T3 only
    private static final String SHORT_DESCRIPTION = "Health regen I";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.RING_HEALTH_REGEN)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .maxValue(MAX_VALUE)
            .maxLevel(MAX_LEVEL)
            .description("+{value} health regen")
            .valueDisplayFormat(ValueDisplayFormat.RAW_NUMBER)
            .build();

    private final EffectProcessor processor = new HealthRegenRingProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.RING_HEALTH_REGEN;
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
