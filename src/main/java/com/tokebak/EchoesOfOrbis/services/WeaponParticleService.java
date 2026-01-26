package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.protocol.ModelParticle;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.entities.SpawnModelParticles;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.PlayerUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing visual particle effects on weapons with embued effects.
 * 
 * This service spawns particles on weapons that have FIRE_ON_HIT or POISON_ON_HIT
 * effects, making them visually distinct and exciting.
 * 
 * Particle systems used:
 * - Fire: "FireSword" (same as Weapon_Longsword_Flame)
 * - Poison: "Effect_Poison" (poison status effect particles)
 */
public class WeaponParticleService {
    
    /**
     * Particle system ID for fire effect on weapons.
     * Uses the same system as the Flame Longsword.
     */
    private static final String FIRE_PARTICLE_SYSTEM = "FireSword";
    
    /**
     * Particle system ID for poison effect on weapons.
     * Uses the melee poison slash effect (green glow and lines).
     * Avoids using Effect_Poison which has skull particles.
     */
    private static final String POISON_PARTICLE_SYSTEM = "Placeholder_Poison_Melee_Slash";
    
    /**
     * Cooldown between particle spawns per player in milliseconds.
     * Particles need to be refreshed periodically since SpawnModelParticles
     * creates one-shot particles, not persistent ones.
     * Lower value = more frequent particles but more network traffic.
     */
    private static final long PARTICLE_REFRESH_MS = 200;
    
    /**
     * Tracks last particle spawn time per player UUID to avoid spamming.
     */
    private final Map<String, Long> lastParticleSpawn = new ConcurrentHashMap<>();
    
    private final ItemExpService itemExpService;
    
    public WeaponParticleService(@Nonnull final ItemExpService itemExpService) {
        this.itemExpService = itemExpService;
    }
    
    /**
     * Spawns weapon particles for a player if they have embued effects on their weapon.
     * This should be called periodically (e.g., during combat or on tick).
     * 
     * @param playerRef The player reference
     * @param playerEntityRef The player's entity reference
     * @param weapon The weapon ItemStack (already verified to be valid)
     * @param componentAccessor Component accessor for the entity store
     */
    public void trySpawnWeaponParticles(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final Ref<EntityStore> playerEntityRef,
            @Nonnull final ItemStack weapon,
            @Nonnull final ComponentAccessor<EntityStore> componentAccessor
    ) {
        // Check cooldown first (before any logging to reduce spam)
        final String playerKey = playerRef.getUuid().toString();
        final long now = System.currentTimeMillis();
        final Long lastSpawn = this.lastParticleSpawn.get(playerKey);
        
        if (lastSpawn != null && (now - lastSpawn) < PARTICLE_REFRESH_MS) {
            return; // Still on cooldown
        }
        
        // Get unlocked effects for this weapon
        final List<WeaponEffectType> effects = this.itemExpService.getUnlockedEffects(weapon);
        if (effects == null || effects.isEmpty()) {
            // Only log once per second to reduce spam (use 5x the cooldown)
            if (lastSpawn == null || (now - lastSpawn) > PARTICLE_REFRESH_MS * 5) {
                System.out.println("[WeaponParticles] No unlocked effects on weapon: " + weapon.getItemId());
            }
            this.lastParticleSpawn.put(playerKey, now);
            return;
        }
        
        // Check for fire and poison effects
        final boolean hasFire = effects.contains(WeaponEffectType.FIRE_ON_HIT);
        final boolean hasPoison = effects.contains(WeaponEffectType.POISON_ON_HIT);
        
        if (!hasFire && !hasPoison) {
            // Only log once per second to reduce spam
            if (lastSpawn == null || (now - lastSpawn) > PARTICLE_REFRESH_MS * 5) {
                System.out.println("[WeaponParticles] Weapon has effects " + effects + " but no fire/poison");
            }
            this.lastParticleSpawn.put(playerKey, now);
            return;
        }
        
        // Get player's network ID
        final NetworkId networkId = (NetworkId) componentAccessor.getComponent(
                playerEntityRef,
                NetworkId.getComponentType()
        );
        if (networkId == null) {
            System.out.println("[WeaponParticles] Could not get NetworkId for player");
            return;
        }
        
        // Build particle array
        final Set<ModelParticle> particles = new HashSet<>();
        
        if (hasFire) {
            particles.add(createWeaponParticle(FIRE_PARTICLE_SYSTEM));
        }
        if (hasPoison) {
            particles.add(createWeaponParticle(POISON_PARTICLE_SYSTEM));
        }
        
        if (particles.isEmpty()) {
            return;
        }
        
        // Create and send the packet
        final ModelParticle[] particleArray = particles.toArray(new ModelParticle[0]);
        final SpawnModelParticles packet = new SpawnModelParticles(
                networkId.getId(),
                particleArray
        );
        
        System.out.println("[WeaponParticles] Spawning " + particleArray.length + " particle system(s) - " +
                (hasFire ? "FIRE " : "") + (hasPoison ? "POISON" : ""));
        
        // Broadcast to all players who can see this player
        PlayerUtil.broadcastPacketToPlayers(componentAccessor, packet);
        
        // Update cooldown
        this.lastParticleSpawn.put(playerKey, now);
    }
    
    /**
     * Creates a ModelParticle configured for weapon attachment.
     */
    private ModelParticle createWeaponParticle(@Nonnull final String systemId) {
        final ModelParticle particle = new ModelParticle();
        particle.systemId = systemId;
        particle.scale = 1.0f;
        particle.targetEntityPart = EntityPart.PrimaryItem;
        particle.targetNodeName = "Handle"; // Most weapons have a Handle node
        particle.positionOffset = new Vector3f(0.5f, 0.0f, 0.0f); // Offset along blade
        particle.detachedFromModel = false;
        return particle;
    }
    
    /**
     * Clears the particle cooldown cache.
     * Called when the service is being cleaned up.
     */
    public void clearCache() {
        this.lastParticleSpawn.clear();
    }
    
    /**
     * Check if a weapon has any visual effects (fire or poison).
     */
    public boolean hasVisualEffects(@Nullable final ItemStack weapon) {
        if (weapon == null) {
            return false;
        }
        final List<WeaponEffectType> effects = this.itemExpService.getUnlockedEffects(weapon);
        if (effects == null || effects.isEmpty()) {
            return false;
        }
        return effects.contains(WeaponEffectType.FIRE_ON_HIT) ||
               effects.contains(WeaponEffectType.POISON_ON_HIT);
    }
}
