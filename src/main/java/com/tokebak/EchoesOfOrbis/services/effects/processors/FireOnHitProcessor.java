package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Processor for FIRE_ON_HIT effect.
 * 
 * Gives a chance to apply a burn/fire status effect to the target on hit.
 * Uses the game's built-in Burn EntityEffect system.
 * 
 * The Burn effect:
 * - Deals 5 fire damage every 1 second
 * - Lasts 3 seconds (15 total damage)
 * - Uses Overwrite overlap behavior (resets duration on re-application)
 * - Cannot be applied if target is in water (game handles this)
 * 
 * The effect value from WeaponEffectDefinition represents the CHANCE to apply burn (0.0 to 1.0).
 */
public class FireOnHitProcessor implements EffectProcessor {
    
    /**
     * The IDs of burn EntityEffect assets in the game, in order of preference.
     * - Burn: 5 damage/sec for 3 sec (standard)
     * - Flame_Staff_Burn: 1 damage/2sec for 3 sec (weaker, from flame staff)
     */
    private static final String[] BURN_EFFECT_IDS = {
            "Burn",
            "Flame_Staff_Burn",
    };
    
    /**
     * Cooldown between burn applications in milliseconds.
     * Shorter than poison since burn has Overwrite behavior anyway.
     */
    private static final long BURN_COOLDOWN_MS = 3000;
    
    private final Random random = new Random();
    
    /**
     * Cached reference to the burn EntityEffect asset.
     */
    private EntityEffect cachedBurnEffect = null;
    private String cachedBurnEffectId = null;
    private boolean effectLookupAttempted = false;
    
    /**
     * Cooldown tracker per target entity.
     */
    private final Map<Integer, Long> targetCooldowns = new java.util.HashMap<>();
    private long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000;
    
    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Calculate burn chance based on effect level
        final double burnChance = definition.calculateValue(instance.getLevel());
        
        if (burnChance <= 0) {
            return;
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        if (roll >= burnChance) {
            return;
        }
        
        // Check cooldown for this target
        final int targetKey = context.getTargetRef().hashCode();
        final long now = System.currentTimeMillis();
        
        // Periodically clean up old cooldown entries
        if (now - this.lastCleanup > CLEANUP_INTERVAL_MS) {
            this.cleanupOldCooldowns(now);
            this.lastCleanup = now;
        }
        
        final Long lastApplication = this.targetCooldowns.get(targetKey);
        if (lastApplication != null && (now - lastApplication) < BURN_COOLDOWN_MS) {
            return; // Still on cooldown
        }
        
        // Get or look up the burn effect
        final EntityEffect burnEffect = this.getBurnEffect();
        if (burnEffect == null) {
            return;
        }
        
        // Get the target's EffectControllerComponent
        final EffectControllerComponent effectController = (EffectControllerComponent) context.getCommandBuffer()
                .getComponent(context.getTargetRef(), EffectControllerComponent.getComponentType());
        
        if (effectController == null) {
            return;
        }
        
        // Apply burn using the game's standard method
        // Note: The game will automatically reject this if the target is in water
        final boolean applied = effectController.addEffect(
                context.getTargetRef(),
                burnEffect,
                context.getCommandBuffer()
        );
        
        if (applied) {
            this.targetCooldowns.put(targetKey, now);
            System.out.println(String.format(
                    "[WeaponEffect] FIRE_ON_HIT: Applied %s (%.0f%% chance)",
                    this.cachedBurnEffectId,
                    burnChance * 100
            ));
        }
    }
    
    private EntityEffect getBurnEffect() {
        if (this.cachedBurnEffect != null) {
            return this.cachedBurnEffect;
        }
        
        if (this.effectLookupAttempted) {
            return null;
        }
        
        this.effectLookupAttempted = true;
        
        for (final String effectId : BURN_EFFECT_IDS) {
            this.cachedBurnEffect = (EntityEffect) EntityEffect.getAssetMap().getAsset(effectId);
            if (this.cachedBurnEffect != null) {
                this.cachedBurnEffectId = effectId;
                System.out.println("[WeaponEffect] Found burn effect: " + effectId);
                return this.cachedBurnEffect;
            }
        }
        
        System.out.println("[WeaponEffect] Warning: No burn EntityEffect found.");
        return null;
    }
    
    private void cleanupOldCooldowns(final long now) {
        final Iterator<Map.Entry<Integer, Long>> it = this.targetCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > BURN_COOLDOWN_MS * 2) {
                it.remove();
            }
        }
    }
}
