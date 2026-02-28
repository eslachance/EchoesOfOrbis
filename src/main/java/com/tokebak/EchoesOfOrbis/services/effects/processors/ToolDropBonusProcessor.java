package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;

/**
 * Processor for TOOL_DROP_BONUS effect.
 * The effect is applied in ToolBreakBlockEventSystem when blocks are broken with the tool.
 * This processor is a no-op for onDamageDealt (tools do not deal entity damage).
 */
public class ToolDropBonusProcessor implements EffectProcessor {

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // No-op: TOOL_DROP_BONUS is applied in ToolBreakBlockEventSystem on BreakBlockEvent
    }
}
