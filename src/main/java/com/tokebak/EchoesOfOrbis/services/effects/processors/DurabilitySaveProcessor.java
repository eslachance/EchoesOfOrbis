package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import com.tokebak.EchoesOfOrbis.systems.DurabilitySaveRestoreSystem;
import javax.annotation.Nonnull;
import java.util.Random;

/**
 * Processor for DURABILITY_SAVE effect.
 * 
 * Gives a chance to not lose durability on the weapon when dealing damage.
 * When the save is successful, this sets a flag on the damage event that
 * DurabilitySaveRestoreSystem will read to restore durability after it's lost.
 * 
 * TEMPORARY: For testing the effect system.
 */
public class DurabilitySaveProcessor implements EffectProcessor {
    
    private final Random random = new Random();
    
    @Override
    @SuppressWarnings("deprecation")
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        final Damage damage = context.getOriginalDamage();
        
        // Check if this damage cause even has durability loss
        // Note: getCause() is deprecated but still functional
        final DamageCause cause = damage.getCause();
        if (cause == null || !cause.isDurabilityLoss()) {
            return; // No durability would be lost anyway
        }
        
        // Calculate save chance based on effect level
        final double saveChance = definition.calculateValue(instance.getLevel());
        
        if (saveChance <= 0) {
            return; // No chance to save
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        final boolean saved = roll < saveChance;
        
        if (saved) {
            // Mark the damage event for durability restoration
            // ItemExpDamageSystem will read this flag and pre-add durability
            // to counteract the durability loss that happens later
            damage.getMetaStore().putMetaObject(
                    DurabilitySaveRestoreSystem.RESTORE_DURABILITY, 
                    Boolean.TRUE
            );
            System.out.println(String.format(
                    "[WeaponEffect] DURABILITY_SAVE: Saved! (%.0f%% chance, rolled %.2f)",
                    saveChance * 100, roll
            ));
        }
    }
}
