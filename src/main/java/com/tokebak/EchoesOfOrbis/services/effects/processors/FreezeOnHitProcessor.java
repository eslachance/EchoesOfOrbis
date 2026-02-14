package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Processor for FREEZE_ON_HIT effect.
 * 
 * Gives a chance to apply a freeze status effect to the target on hit.
 * Uses the game's built-in Freeze EntityEffect system.
 * 
 * The Freeze effect:
 * - Completely immobilizes the target (DisableAll movement)
 * - Has ice/snow visual effects
 * - More powerful than Slow, so lower chance and longer cooldown
 * 
 * The effect value from WeaponEffectDefinition represents the CHANCE to apply freeze (0.0 to 1.0).
 */
public class FreezeOnHitProcessor implements EffectProcessor {
    
    /**
     * The ID of the Freeze EntityEffect asset in the game.
     */
    private static final String FREEZE_EFFECT_ID = "Freeze";
    
    /**
     * Cooldown between freeze applications in milliseconds.
     * Longer cooldown since freeze is very powerful.
     */
    private static final long FREEZE_COOLDOWN_MS = 10000;
    
    private final Random random = new Random();
    
    /**
     * Cached reference to the Freeze EntityEffect asset.
     */
    private EntityEffect cachedFreezeEffect = null;
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
        // Calculate freeze chance based on effect level
        final double freezeChance = definition.calculateValue(instance.getLevel());
        
        if (freezeChance <= 0) {
            return;
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        if (roll >= freezeChance) {
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
        if (lastApplication != null && (now - lastApplication) < FREEZE_COOLDOWN_MS) {
            return; // Still on cooldown
        }
        
        // Get or look up the freeze effect
        final EntityEffect freezeEffect = this.getFreezeEffect();
        if (freezeEffect == null) {
            return;
        }
        
        // Get the target's EffectControllerComponent
        final EffectControllerComponent effectController = (EffectControllerComponent) context.getCommandBuffer()
                .getComponent(context.getTargetRef(), EffectControllerComponent.getComponentType());
        
        if (effectController == null) {
            return;
        }
        
        // Apply freeze using the game's standard method
        final boolean applied = effectController.addEffect(
                context.getTargetRef(),
                freezeEffect,
                context.getCommandBuffer()
        );
        
        if (applied) {
            this.targetCooldowns.put(targetKey, now);
            System.out.println(String.format(
                    "[WeaponEffect] FREEZE_ON_HIT: Applied Freeze (%.0f%% chance)",
                    freezeChance * 100
            ));
        }
    }
    
    private EntityEffect getFreezeEffect() {
        if (this.cachedFreezeEffect != null) {
            return this.cachedFreezeEffect;
        }
        
        if (this.effectLookupAttempted) {
            return null;
        }
        
        this.effectLookupAttempted = true;
        this.cachedFreezeEffect = (EntityEffect) EntityEffect.getAssetMap().getAsset(FREEZE_EFFECT_ID);
        
        if (this.cachedFreezeEffect != null) {
            System.out.println("[WeaponEffect] Found freeze effect: " + FREEZE_EFFECT_ID);
        } else {
            System.out.println("[WeaponEffect] Warning: Freeze EntityEffect not found.");
        }
        
        return this.cachedFreezeEffect;
    }
    
    private void cleanupOldCooldowns(final long now) {
        final Iterator<Map.Entry<Integer, Long>> it = this.targetCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > FREEZE_COOLDOWN_MS * 2) {
                it.remove();
            }
        }
    }
}
