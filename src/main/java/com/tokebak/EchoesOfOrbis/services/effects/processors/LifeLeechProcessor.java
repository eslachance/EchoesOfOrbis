package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;

/**
 * Processor for LIFE_LEECH effect.
 * 
 * Heals the attacker for a percentage of damage dealt.
 * 
 * Example: If effect value is 0.10 (10%) and damage dealt is 50,
 * the attacker will be healed for 5 HP.
 */
public class LifeLeechProcessor implements EffectProcessor {
    
    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Calculate heal amount based on effect level
        final double leechPercent = definition.calculateValue(instance.getLevel());
        
        if (leechPercent <= 0) {
            return; // No healing to apply
        }
        
        // Calculate heal amount from damage dealt
        final float damageDealt = context.getOriginalDamageAmount();
        final float healAmount = (float) (damageDealt * leechPercent);
        
        if (healAmount < 0.1f) {
            return; // Skip tiny heal amounts
        }
        
        // Get the attacker's stat map to modify health
        final EntityStatMap attackerStats = (EntityStatMap) context.getStore().getComponent(
                context.getAttackerRef(),
                EntityStatsModule.get().getEntityStatMapComponentType()
        );
        
        if (attackerStats == null) {
            return; // Can't heal if no stats component
        }
        
        // Get the health stat index
        final int healthStatIndex = DefaultEntityStatTypes.getHealth();
        
        // Add health to the attacker
        attackerStats.addStatValue(healthStatIndex, healAmount);
        
        // Debug logging
        System.out.println(String.format(
                "[WeaponEffect] LIFE_LEECH: %.0f%% of %.2f damage = %.2f HP healed",
                leechPercent * 100,
                damageDealt,
                healAmount
        ));
    }
}
