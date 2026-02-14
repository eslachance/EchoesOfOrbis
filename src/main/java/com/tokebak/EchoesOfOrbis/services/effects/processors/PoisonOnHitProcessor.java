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
 * Processor for POISON_ON_HIT effect.
 * 
 * Gives a chance to apply a poison status effect to the target on hit.
 * Uses the game's built-in Poison EntityEffect system.
 * 
 * NOTE: There is a known game bug where the particle effects (skulls) don't properly
 * clear on mobs when poison expires. The damage and tint work correctly.
 * 
 * The effect value from WeaponEffectDefinition represents the CHANCE to apply poison (0.0 to 1.0).
 * The actual poison damage per tick is determined by the game's EntityEffect asset.
 */
public class PoisonOnHitProcessor implements EffectProcessor {
    
    /**
     * The IDs of poison EntityEffect assets in the game, in order of preference.
     * Poison_T1 = 6 damage/5sec, Poison_T2 = 12 damage/5sec, Poison = 10 damage/5sec
     */
    private static final String[] POISON_EFFECT_IDS = {
            "Poison_T1",
            "Poison_T2",
            "Poison",
    };
    
    /**
     * Cooldown between poison applications in milliseconds.
     * Prevents spam-applying poison every single hit.
     */
    private static final long POISON_COOLDOWN_MS = 8000;
    
    private final Random random = new Random();
    
    /**
     * Cached reference to the poison EntityEffect asset.
     */
    private EntityEffect cachedPoisonEffect = null;
    private String cachedPoisonEffectId = null;
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
        // Calculate poison chance based on effect level
        final double poisonChance = definition.calculateValue(instance.getLevel());
        
        if (poisonChance <= 0) {
            return;
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        if (roll >= poisonChance) {
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
        if (lastApplication != null && (now - lastApplication) < POISON_COOLDOWN_MS) {
            return; // Still on cooldown
        }
        
        // Get or look up the poison effect
        final EntityEffect poisonEffect = this.getPoisonEffect();
        if (poisonEffect == null) {
            return;
        }
        
        // Get the target's EffectControllerComponent
        final EffectControllerComponent effectController = (EffectControllerComponent) context.getCommandBuffer()
                .getComponent(context.getTargetRef(), EffectControllerComponent.getComponentType());
        
        if (effectController == null) {
            return;
        }
        
        // Apply poison using the game's standard method
        final boolean applied = effectController.addEffect(
                context.getTargetRef(),
                poisonEffect,
                context.getCommandBuffer()
        );
        
        if (applied) {
            this.targetCooldowns.put(targetKey, now);
            System.out.println(String.format(
                    "[WeaponEffect] POISON_ON_HIT: Applied %s (%.0f%% chance)",
                    this.cachedPoisonEffectId,
                    poisonChance * 100
            ));
        }
    }
    
    private EntityEffect getPoisonEffect() {
        if (this.cachedPoisonEffect != null) {
            return this.cachedPoisonEffect;
        }
        
        if (this.effectLookupAttempted) {
            return null;
        }
        
        this.effectLookupAttempted = true;
        
        for (final String effectId : POISON_EFFECT_IDS) {
            this.cachedPoisonEffect = (EntityEffect) EntityEffect.getAssetMap().getAsset(effectId);
            if (this.cachedPoisonEffect != null) {
                this.cachedPoisonEffectId = effectId;
                System.out.println("[WeaponEffect] Found poison effect: " + effectId);
                return this.cachedPoisonEffect;
            }
        }
        
        System.out.println("[WeaponEffect] Warning: No poison EntityEffect found.");
        return null;
    }
    
    private void cleanupOldCooldowns(final long now) {
        final Iterator<Map.Entry<Integer, Long>> it = this.targetCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > POISON_COOLDOWN_MS * 2) {
                it.remove();
            }
        }
    }
}
