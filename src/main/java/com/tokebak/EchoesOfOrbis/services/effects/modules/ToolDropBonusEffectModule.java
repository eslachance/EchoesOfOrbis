package com.tokebak.EchoesOfOrbis.services.effects.modules;

import com.tokebak.EchoesOfOrbis.services.effects.EffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.ValueDisplayFormat;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.ToolDropBonusProcessor;
import javax.annotation.Nonnull;

/** TOOL_DROP_BONUS: bonus percent to items dropped when breaking blocks with this tool. Applied in ToolBreakBlockEventSystem. */
public class ToolDropBonusEffectModule implements EffectModule {

    private static final double BASE_VALUE = 0.05;
    private static final double VALUE_PER_LEVEL = 0.05;
    private static final String DESCRIPTION_TEMPLATE = "+{value} bonus to block drops";
    private static final String SHORT_DESCRIPTION = "Bonus percent to items dropped when breaking blocks";

    private final WeaponEffectDefinition definition = WeaponEffectDefinition.builder(WeaponEffectType.TOOL_DROP_BONUS)
            .baseValue(BASE_VALUE)
            .valuePerLevel(VALUE_PER_LEVEL)
            .description(DESCRIPTION_TEMPLATE)
            .valueDisplayFormat(ValueDisplayFormat.PERCENT)
            .build();

    private final EffectProcessor processor = new ToolDropBonusProcessor();

    @Override
    @Nonnull
    public WeaponEffectType getType() {
        return WeaponEffectType.TOOL_DROP_BONUS;
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
