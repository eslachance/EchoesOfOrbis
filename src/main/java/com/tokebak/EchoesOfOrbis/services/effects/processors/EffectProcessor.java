package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;

/**
 * Interface for effect processors.
 * Each effect type has a processor that knows how to apply it.
 * 
 * Processors are stateless and should not store any per-weapon data.
 * All effect state is stored in WeaponEffectInstance on the weapon itself.
 */
public interface EffectProcessor {
    
    /**
     * Called when damage is dealt by a weapon with this effect.
     * This is where most combat effects should be applied.
     * 
     * @param context The effect context containing damage info, attacker, target, etc.
     * @param instance The effect instance from the weapon (contains effect level)
     * @param definition The global definition for this effect type
     */
    void onDamageDealt(
            @Nonnull EffectContext context,
            @Nonnull WeaponEffectInstance instance,
            @Nonnull WeaponEffectDefinition definition
    );
    
    /**
     * Called when a weapon with this effect is equipped.
     * Used for "while held" effects like stat buffs.
     * 
     * Default implementation does nothing.
     * 
     * @param context The effect context
     * @param instance The effect instance from the weapon
     * @param definition The global definition for this effect type
     */
    default void onWeaponEquipped(
            @Nonnull EffectContext context,
            @Nonnull WeaponEffectInstance instance,
            @Nonnull WeaponEffectDefinition definition
    ) {
        // Default: no-op
    }
    
    /**
     * Called when a weapon with this effect is unequipped.
     * Used to remove "while held" effects.
     * 
     * Default implementation does nothing.
     * 
     * @param context The effect context
     * @param instance The effect instance from the weapon
     * @param definition The global definition for this effect type
     */
    default void onWeaponUnequipped(
            @Nonnull EffectContext context,
            @Nonnull WeaponEffectInstance instance,
            @Nonnull WeaponEffectDefinition definition
    ) {
        // Default: no-op
    }
}
