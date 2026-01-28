package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Processor for MULTISHOT effect.
 * 
 * Gives a chance to fire an additional projectile when a projectile weapon deals damage.
 * The extra projectile is spawned from the attacker's position in their look direction,
 * using the same projectile type as determined from the arrow items in their inventory.
 * 
 * This effectively gives the player a "free follow-up shot" without consuming ammo.
 * 
 * Only applies to PROJECTILE category weapons (bows, crossbows, guns, etc.).
 * 
 * The effect value from WeaponEffectDefinition represents the CHANCE to trigger (0.0 to 1.0).
 */
public class MultishotProcessor implements EffectProcessor {
    
    /**
     * Projectile asset names to try, in order of preference.
     * These are derived from common arrow item patterns.
     */
    private static final String[] PROJECTILE_ASSET_NAMES = {
            // Try exact matches first
            "Arrow_Crude",
            "Arrow_Basic", 
            "Arrow_Iron",
            "Arrow_Steel",
            "Arrow",
            // Crossbow bolts
            "Bolt_Crude",
            "Bolt_Basic",
            "Bolt",
            // Generic projectiles
            "Projectile",
    };
    
    /**
     * Cooldown between multishot triggers in milliseconds.
     * Prevents rapid-fire multishot from being too powerful.
     */
    private static final long MULTISHOT_COOLDOWN_MS = 500;
    
    /**
     * Slight offset angle for the extra projectile (in radians).
     * This makes the multishot visually distinct and prevents exact overlap.
     */
    private static final float MULTISHOT_ANGLE_OFFSET = 0.025f; // ~1.5 degrees
    
    private final Random random = new Random();
    
    /**
     * Cached projectile asset name that works.
     */
    private String cachedProjectileAsset = null;
    private boolean lookupAttempted = false;
    
    /**
     * Cooldown tracker per attacker entity.
     */
    private final Map<Integer, Long> attackerCooldowns = new java.util.HashMap<>();
    private long lastCleanup = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000;
    
    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Only apply to projectile weapons
        if (context.getWeaponCategory() != WeaponCategory.PROJECTILE) {
            return;
        }
        
        // Calculate multishot chance based on effect level
        final double multishotChance = definition.calculateValue(instance.getLevel());
        
        if (multishotChance <= 0) {
            return;
        }
        
        // Roll the dice
        final double roll = this.random.nextDouble();
        if (roll >= multishotChance) {
            return; // Didn't proc
        }
        
        // Check cooldown for this attacker
        final int attackerKey = context.getAttackerRef().hashCode();
        final long now = System.currentTimeMillis();
        
        // Periodically clean up old cooldown entries
        if (now - this.lastCleanup > CLEANUP_INTERVAL_MS) {
            this.cleanupOldCooldowns(now);
            this.lastCleanup = now;
        }
        
        final Long lastTrigger = this.attackerCooldowns.get(attackerKey);
        if (lastTrigger != null && (now - lastTrigger) < MULTISHOT_COOLDOWN_MS) {
            return; // Still on cooldown
        }
        
        // Get the projectile asset name - try from player's arrows first, then fallback
        final String projectileAssetName = this.getProjectileAssetName(context);
        if (projectileAssetName == null) {
            System.out.println("[WeaponEffect] MULTISHOT: No valid projectile asset found");
            return;
        }
        
        // Get attacker's position and look direction
        final Vector3d spawnPosition = this.getAttackerPosition(context);
        final Vector3f rotation = this.getAttackerRotation(context);
        
        if (spawnPosition == null || rotation == null) {
            System.out.println("[WeaponEffect] MULTISHOT: Could not get attacker position/rotation");
            return;
        }
        
        // Apply a small random offset to the rotation for variety
        this.applyRandomRotationOffset(rotation);
        
        // Spawn the extra projectile
        try {
            // Get the time resource for projectile creation
            final TimeResource timeResource = (TimeResource) context.getCommandBuffer()
                    .getResource(TimeResource.getResourceType());
            
            if (timeResource == null) {
                System.out.println("[WeaponEffect] MULTISHOT: Could not get TimeResource");
                return;
            }
            
            System.out.println(String.format(
                    "[WeaponEffect] MULTISHOT: Spawning at pos=(%.2f, %.2f, %.2f) rot=(yaw=%.2f, pitch=%.2f)",
                    spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ(),
                    rotation.getYaw(), rotation.getPitch()
            ));
            
            // Create the projectile holder
            final Holder<EntityStore> projectileHolder = ProjectileComponent.assembleDefaultProjectile(
                    timeResource,
                    projectileAssetName,
                    spawnPosition,
                    rotation
            );
            
            // Get the projectile component and initialize it
            final ProjectileComponent projectileComponent = (ProjectileComponent) projectileHolder
                    .getComponent(ProjectileComponent.getComponentType());
            
            if (projectileComponent == null) {
                System.out.println("[WeaponEffect] MULTISHOT: ProjectileComponent is null");
                return;
            }
            
            // Initialize the projectile (loads the projectile asset)
            if (!projectileComponent.initialize()) {
                System.out.println("[WeaponEffect] MULTISHOT: Failed to initialize projectile: " + projectileAssetName);
                // Clear cache so we try other options next time
                if (projectileAssetName.equals(this.cachedProjectileAsset)) {
                    this.cachedProjectileAsset = null;
                    this.lookupAttempted = false;
                }
                return;
            }
            
            // Get projectile config for debug info
            final Projectile projectileConfig = projectileComponent.getProjectile();
            if (projectileConfig != null) {
                System.out.println(String.format(
                        "[WeaponEffect] MULTISHOT: Projectile config - muzzleVelocity=%.2f, appearance=%s",
                        projectileConfig.getMuzzleVelocity(),
                        projectileComponent.getAppearance()
                ));
            }
            
            // Add NetworkId - CRITICAL for the entity to sync to clients
            // Try getting EntityStore from store's external data (more reliable than CommandBuffer)
            try {
                final EntityStore entityStore = (EntityStore) context.getStore().getExternalData();
                if (entityStore != null) {
                    final int networkId = entityStore.takeNextNetworkId();
                    projectileHolder.putComponent(
                            NetworkId.getComponentType(),
                            new NetworkId(networkId)
                    );
                    System.out.println("[WeaponEffect] MULTISHOT: Added NetworkId: " + networkId);
                } else {
                    System.out.println("[WeaponEffect] MULTISHOT: WARNING - EntityStore is null, no NetworkId added!");
                }
            } catch (final Exception e) {
                System.err.println("[WeaponEffect] MULTISHOT: Error adding NetworkId: " + e.getMessage());
            }
            
            // Get the attacker's UUID for setting as the projectile's creator
            final UUIDComponent attackerUuid = (UUIDComponent) context.getCommandBuffer()
                    .getComponent(context.getAttackerRef(), UUIDComponent.getComponentType());
            
            final UUID creatorUuid = attackerUuid != null ? attackerUuid.getUuid() : UUID.randomUUID();
            
            // Shoot the projectile (sets velocity based on rotation)
            projectileComponent.shoot(
                    projectileHolder,
                    creatorUuid,
                    spawnPosition.getX(),
                    spawnPosition.getY(),
                    spawnPosition.getZ(),
                    rotation.getYaw(),
                    rotation.getPitch()
            );
            
            // Check the velocity after shooting
            final com.hypixel.hytale.server.core.modules.physics.component.Velocity velocity = 
                    (com.hypixel.hytale.server.core.modules.physics.component.Velocity) projectileHolder
                    .getComponent(com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType());
            if (velocity != null) {
                System.out.println(String.format(
                        "[WeaponEffect] MULTISHOT: After shoot - velocity component exists"
                ));
            }
            
            // Add the projectile entity to the world
            final var projectileRef = context.getCommandBuffer().addEntity(projectileHolder, AddReason.SPAWN);
            
            System.out.println(String.format(
                    "[WeaponEffect] MULTISHOT: Entity added to world, ref valid: %s",
                    projectileRef != null && projectileRef.isValid()
            ));
            
            // Update cooldown and cache the working asset name
            this.attackerCooldowns.put(attackerKey, now);
            this.cachedProjectileAsset = projectileAssetName;
            
            System.out.println(String.format(
                    "[WeaponEffect] MULTISHOT: Fired extra %s (%.0f%% chance)",
                    projectileAssetName,
                    multishotChance * 100
            ));
            
        } catch (final Exception e) {
            System.err.println("[WeaponEffect] MULTISHOT: Error spawning projectile: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get a projectile asset name to use.
     * First tries to derive from player's arrow items, then falls back to defaults.
     */
    @Nullable
    private String getProjectileAssetName(@Nonnull final EffectContext context) {
        // If we have a cached working asset, use it
        if (this.cachedProjectileAsset != null) {
            return this.cachedProjectileAsset;
        }
        
        // Try to find arrow items in the player's inventory
        final String arrowBasedAsset = this.getProjectileFromPlayerArrows(context);
        if (arrowBasedAsset != null) {
            // Verify this projectile actually exists
            final Projectile projectile = (Projectile) Projectile.getAssetMap().getAsset(arrowBasedAsset);
            if (projectile != null) {
                System.out.println("[WeaponEffect] MULTISHOT: Found projectile from arrow: " + arrowBasedAsset);
                return arrowBasedAsset;
            }
        }
        
        // Fall back to trying known projectile names
        if (!this.lookupAttempted) {
            this.lookupAttempted = true;
            
            // FIRST: Enumerate ALL available projectile assets so we can find the right one
            System.out.println("[WeaponEffect] MULTISHOT: Enumerating ALL available projectile assets...");
            try {
                final Map<?, ?> allProjectiles = Projectile.getAssetMap().getAssetMap();
                System.out.println("[WeaponEffect] MULTISHOT: Found " + allProjectiles.size() + " total projectile assets:");
                
                String arrowAsset = null;
                for (final Object key : allProjectiles.keySet()) {
                    final String assetId = key.toString();
                    System.out.println("  - " + assetId);
                    
                    // Prefer anything with "Arrow" in the name
                    if (assetId.toLowerCase().contains("arrow")) {
                        if (arrowAsset == null) {
                            arrowAsset = assetId;
                        }
                    }
                }
                
                // If we found an arrow-specific asset, use that instead of generic "Projectile"
                if (arrowAsset != null) {
                    System.out.println("[WeaponEffect] MULTISHOT: Using arrow asset: " + arrowAsset);
                    this.cachedProjectileAsset = arrowAsset;
                    return arrowAsset;
                }
            } catch (final Exception e) {
                System.out.println("[WeaponEffect] MULTISHOT: Error enumerating: " + e.getMessage());
            }
            
            // Fallback: try known projectile names
            System.out.println("[WeaponEffect] MULTISHOT: No arrow asset found, trying known names...");
            
            for (final String assetName : PROJECTILE_ASSET_NAMES) {
                final Projectile projectile = (Projectile) Projectile.getAssetMap().getAsset(assetName);
                if (projectile != null) {
                    System.out.println("[WeaponEffect] MULTISHOT: Using fallback: " + assetName);
                    this.cachedProjectileAsset = assetName;
                    return assetName;
                }
            }
            
            System.out.println("[WeaponEffect] MULTISHOT: No valid projectile assets found!");
        }
        
        return this.cachedProjectileAsset;
    }
    
    /**
     * Try to derive a projectile asset name from the player's arrow items.
     * Searches inventory for items containing "Arrow" and converts to projectile name.
     */
    @Nullable
    private String getProjectileFromPlayerArrows(@Nonnull final EffectContext context) {
        try {
            // Get the player component
            final Player player = (Player) context.getCommandBuffer()
                    .getComponent(context.getAttackerRef(), Player.getComponentType());
            
            if (player == null) {
                return null;
            }
            
            final Inventory inventory = player.getInventory();
            if (inventory == null) {
                return null;
            }
            
            // Search hotbar first, then storage
            String arrowItemId = this.findArrowInContainer(inventory.getHotbar());
            if (arrowItemId == null) {
                arrowItemId = this.findArrowInContainer(inventory.getStorage());
            }
            
            if (arrowItemId == null) {
                return null;
            }
            
            System.out.println("[WeaponEffect] MULTISHOT: Found arrow item: " + arrowItemId);
            
            // Try to convert arrow item ID to projectile asset name
            // e.g., "Weapon_Arrow_Crude" -> "Arrow_Crude"
            //       "Weapon_Arrow_Basic" -> "Arrow_Basic"
            return this.convertArrowItemToProjectile(arrowItemId);
            
        } catch (final Exception e) {
            System.out.println("[WeaponEffect] MULTISHOT: Error finding arrows: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Find the first arrow item in a container.
     */
    @Nullable
    private String findArrowInContainer(@Nonnull final ItemContainer container) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            
            final String itemId = item.getItem().getId();
            if (itemId != null && itemId.contains("Arrow")) {
                return itemId;
            }
        }
        return null;
    }
    
    /**
     * Convert an arrow item ID to a projectile asset name.
     * Tries various transformations based on common naming patterns.
     */
    @Nullable
    private String convertArrowItemToProjectile(@Nonnull final String arrowItemId) {
        // Common patterns to try:
        // "Weapon_Arrow_Crude" -> "Arrow_Crude"
        // "Weapon_Arrow_Basic" -> "Arrow_Basic"
        // "Arrow_Crude" -> "Arrow_Crude" (already correct)
        
        String projectileName = arrowItemId;
        
        // Remove "Weapon_" prefix if present
        if (projectileName.startsWith("Weapon_")) {
            projectileName = projectileName.substring(7);
        }
        
        // Try the converted name
        Projectile projectile = (Projectile) Projectile.getAssetMap().getAsset(projectileName);
        if (projectile != null) {
            return projectileName;
        }
        
        // Try just "Arrow" + suffix
        // e.g., "Arrow_Crude" parts: ["Arrow", "Crude"]
        if (projectileName.contains("_")) {
            final String[] parts = projectileName.split("_");
            if (parts.length >= 2 && parts[0].equals("Arrow")) {
                // Already in correct format, maybe try without suffix
                projectile = (Projectile) Projectile.getAssetMap().getAsset("Arrow");
                if (projectile != null) {
                    return "Arrow";
                }
            }
        }
        
        // Try the original item ID as-is (unlikely to work but worth a shot)
        projectile = (Projectile) Projectile.getAssetMap().getAsset(arrowItemId);
        if (projectile != null) {
            return arrowItemId;
        }
        
        System.out.println("[WeaponEffect] MULTISHOT: Could not find projectile for arrow: " + arrowItemId);
        System.out.println("[WeaponEffect] MULTISHOT: Tried: " + projectileName);
        
        return null;
    }
    
    /**
     * Get the attacker's current position for spawning the projectile.
     */
    @Nullable
    private Vector3d getAttackerPosition(@Nonnull final EffectContext context) {
        try {
            // Try to get the look transform which includes proper position for projectile spawning
            final Transform lookTransform = TargetUtil.getLook(
                    context.getAttackerRef(),
                    context.getCommandBuffer()
            );
            if (lookTransform != null) {
                return lookTransform.getPosition();
            }
            
            // Fallback: get from TransformComponent
            final TransformComponent transform = (TransformComponent) context.getCommandBuffer()
                    .getComponent(context.getAttackerRef(), TransformComponent.getComponentType());
            if (transform != null) {
                // Add some height offset for better projectile spawn position
                final Vector3d pos = transform.getPosition().clone();
                pos.y = pos.getY() + 1.5; // Eye height approximately
                return pos;
            }
        } catch (final Exception e) {
            System.err.println("[WeaponEffect] MULTISHOT: Error getting attacker position: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Get the attacker's current rotation (yaw/pitch) for the projectile direction.
     */
    @Nullable
    private Vector3f getAttackerRotation(@Nonnull final EffectContext context) {
        try {
            // Try to get the look transform
            final Transform lookTransform = TargetUtil.getLook(
                    context.getAttackerRef(),
                    context.getCommandBuffer()
            );
            if (lookTransform != null) {
                return lookTransform.getRotation();
            }
            
            // Fallback: get from TransformComponent
            final TransformComponent transform = (TransformComponent) context.getCommandBuffer()
                    .getComponent(context.getAttackerRef(), TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getRotation();
            }
        } catch (final Exception e) {
            System.err.println("[WeaponEffect] MULTISHOT: Error getting attacker rotation: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Apply a small random offset to the rotation for visual variety.
     */
    private void applyRandomRotationOffset(@Nonnull final Vector3f rotation) {
        // Add small random variation to yaw and pitch
        final float yawOffset = (float) ((this.random.nextDouble() - 0.5) * MULTISHOT_ANGLE_OFFSET * 2);
        final float pitchOffset = (float) ((this.random.nextDouble() - 0.5) * MULTISHOT_ANGLE_OFFSET * 2);
        
        rotation.setYaw(rotation.getYaw() + yawOffset);
        rotation.setPitch(rotation.getPitch() + pitchOffset);
    }
    
    /**
     * Clean up old cooldown entries to prevent memory leaks.
     */
    private void cleanupOldCooldowns(final long now) {
        final java.util.Iterator<Map.Entry<Integer, Long>> it = this.attackerCooldowns.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > MULTISHOT_COOLDOWN_MS * 10) {
                it.remove();
            }
        }
    }
    
    /**
     * Reset cached state. Useful for testing.
     */
    public void resetCache() {
        this.attackerCooldowns.clear();
        this.cachedProjectileAsset = null;
        this.lookupAttempted = false;
    }
}
