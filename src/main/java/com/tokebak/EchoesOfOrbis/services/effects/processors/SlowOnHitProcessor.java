package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import com.tokebak.EchoesOfOrbis.utils.EooLogger;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Processor for SLOW_ON_HIT effect.
 * 
 * Gives a chance to apply a slow status effect to the target on hit.
 * Uses the game's built-in Slow EntityEffect system.
 * 
 * The Slow effect:
 * - Reduces movement speed by 50%
 * - Lasts 10 seconds (default)
 * - Has visual tint effects
 * 
 * The effect value from WeaponEffectDefinition represents the CHANCE to apply slow (0.0 to 1.0).
 */
public class SlowOnHitProcessor implements EffectProcessor {
    
    /**
     * The ID of the Slow EntityEffect asset in the game.
     */
    private static final String SLOW_EFFECT_ID = "Slow";
    
    /**
     * Cooldown between slow applications in milliseconds.
     * Prevents stacking/extending too aggressively.
     */
    private static final long SLOW_COOLDOWN_MS = 5000;
    
    private final Random random = new Random();
    
    /**
     * Cached reference to the Slow EntityEffect asset.
     */
    private EntityEffect cachedSlowEffect = null;
    private boolean effectLookupAttempted = false;
    
    /**
     * Cooldown tracker per target entity.
     */
    private final Map<Integer, Long> targetCooldowns = new HashMap<>();
    private long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000;
    
    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Calculate slow chance based on effect level
        final double slowChance = definition.calculateValue(instance.getLevel());
        
        if (slowChance <= 0) {
            return;
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        if (roll >= slowChance) {
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
        if (lastApplication != null && (now - lastApplication) < SLOW_COOLDOWN_MS) {
            return; // Still on cooldown
        }
        
        // Get or look up the slow effect
        final EntityEffect slowEffect = this.getSlowEffect();
        if (slowEffect == null) {
            return;
        }
        
        // Get the target's EffectControllerComponent
        final EffectControllerComponent effectController = (EffectControllerComponent) context.getCommandBuffer()
                .getComponent(context.getTargetRef(), EffectControllerComponent.getComponentType());
        
        if (effectController == null) {
            return;
        }
        
        // Apply slow using the game's standard method
        final boolean applied = effectController.addEffect(
                context.getTargetRef(),
                slowEffect,
                context.getCommandBuffer()
        );
        
        if (applied) {
            this.targetCooldowns.put(targetKey, now);
            EooLogger.debug("SLOW_ON_HIT: Applied Slow (%.0f%% chance)", slowChance * 100);
        }
    }
    
    private EntityEffect getSlowEffect() {
        if (this.cachedSlowEffect != null) {
            return this.cachedSlowEffect;
        }
        
        if (this.effectLookupAttempted) {
            return null;
        }
        
        this.effectLookupAttempted = true;
        this.cachedSlowEffect = (EntityEffect) EntityEffect.getAssetMap().getAsset(SLOW_EFFECT_ID);
        
        if (this.cachedSlowEffect != null) {
            EooLogger.debug("Found slow effect: %s", SLOW_EFFECT_ID);
        } else {
            EooLogger.warn("Slow EntityEffect not found.");
        }
        
        return this.cachedSlowEffect;
    }
    
    private void cleanupOldCooldowns(final long now) {
        final Iterator<Map.Entry<Integer, Long>> it = this.targetCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > SLOW_COOLDOWN_MS * 2) {
                it.remove();
            }
        }
    }
}
