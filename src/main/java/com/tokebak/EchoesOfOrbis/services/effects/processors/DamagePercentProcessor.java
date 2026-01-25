package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;

/**
 * Processor for DAMAGE_PERCENT effect.
 * 
 * Applies bonus damage as a percentage of the original damage.
 * The bonus is applied as a SECOND damage hit to the target,
 * which should trigger hit effects, sounds, and graphics.
 * 
 * Example: If effect value is 0.15 (15%) and original damage is 10,
 * this will deal an additional 1.5 damage hit.
 */
public class DamagePercentProcessor implements EffectProcessor {
    
    /**
     * Meta key to mark damage events as bonus damage from weapon effects.
     * This prevents infinite recursion - bonus damage won't trigger more bonus damage.
     */
    public static final MetaKey<Boolean> IS_BONUS_DAMAGE = Damage.META_REGISTRY.registerMetaObject(
            data -> Boolean.FALSE
    );
    
    /**
     * Check if a damage event is bonus damage from weapon effects.
     */
    public static boolean isBonusDamage(@Nonnull final Damage damage) {
        final Boolean isBonusDamage = (Boolean) damage.getMetaStore().getIfPresentMetaObject(IS_BONUS_DAMAGE);
        return isBonusDamage != null && isBonusDamage;
    }
    
    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Calculate bonus damage percentage based on effect level
        final double percentBonus = definition.calculateValue(instance.getLevel());
        
        if (percentBonus <= 0) {
            return; // No bonus to apply
        }
        
        // Calculate bonus damage amount
        final float originalDamage = context.getOriginalDamageAmount();
        final float bonusDamage = (float) (originalDamage * percentBonus);
        
        if (bonusDamage < 0.1f) {
            return; // Skip tiny damage amounts
        }
        
        // Create a new damage event for the bonus damage
        // Use the same source and damage cause as the original hit
        final Damage originalDamageEvent = context.getOriginalDamage();
        final Damage bonusDamageEvent = new Damage(
                originalDamageEvent.getSource(),
                originalDamageEvent.getDamageCauseIndex(),
                bonusDamage
        );
        
        // Mark this as bonus damage to prevent infinite recursion
        bonusDamageEvent.getMetaStore().putMetaObject(IS_BONUS_DAMAGE, Boolean.TRUE);
        
        // Invoke the bonus damage on the target
        // This should trigger hit effects, sounds, etc.
        context.getCommandBuffer().invoke(
                context.getTargetRef(),
                (EcsEvent) bonusDamageEvent
        );
        
        // Debug logging
        System.out.println(String.format(
                "[WeaponEffect] DAMAGE_PERCENT: +%.0f%% = %.2f bonus damage (from %.2f base)",
                percentBonus * 100,
                bonusDamage,
                originalDamage
        ));
    }
}
