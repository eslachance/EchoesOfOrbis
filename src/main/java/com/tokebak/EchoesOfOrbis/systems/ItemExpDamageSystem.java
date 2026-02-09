package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DamagePercentProcessor;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * System that listens for damage events and:
 * 1. Applies weapon effects (bonus damage, etc.) for leveled weapons
 * 2. Awards XP to the attacking player's weapon
 *
 * This extends DamageEventSystem which is called whenever damage is dealt.
 * We check if the attacker is a player, then process their weapon's effects and XP.
 */
public class ItemExpDamageSystem extends DamageEventSystem {

    private final ItemExpService itemExpService;
    private final EchoesOfOrbisConfig config;
    
    /**
     * Time of inactivity (no damage dealt) before flushing pending XP (in milliseconds).
     * This prevents weapon swaps from interrupting abilities like ultimates.
     * XP is cached during active combat and flushed when combat ends.
     */
    private static final long COMBAT_IDLE_FLUSH_MS = 3000; // 3 seconds
    
    /**
     * Minimum time between damage events to allow level up (in milliseconds).
     * If hits are coming faster than this, we're in rapid-fire combat (like an ultimate)
     * and should delay the level up to avoid interrupting the ability.
     * 50ms is very fast - only ultimates/charged attacks hit this rate.
     */
    private static final long LEVEL_UP_DELAY_THRESHOLD_MS = 50; // 50ms
    
    /**
     * Tracks last damage time per player/slot for combat idle detection.
     * Key: playerUUID:slot, Value: timestamp (ms)
     */
    private final java.util.Map<String, Long> lastDamageTime = new java.util.concurrent.ConcurrentHashMap<>();

    public ItemExpDamageSystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final EchoesOfOrbisConfig config
    ) {
        super();
        this.itemExpService = itemExpService;
        this.config = config;
    }

    /**
     * Called for every damage event in the game.
     * We filter to only process damage dealt BY players.
     */
    @Override
    public void handle(
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final Damage damage
    ) {
        // Skip if damage was cancelled or zero
        if (damage.isCancelled() || damage.getAmount() <= 0) {
            return;
        }
        
        // Skip bonus damage from weapon effects (prevents infinite recursion)
        if (DamagePercentProcessor.isBonusDamage(damage)) {
            return;
        }

        // Get the damage source - we only care about entity sources (players/mobs)
        final Damage.Source source = damage.getSource();

        // Pattern matching: check if source is an EntitySource
        if (!(source instanceof final Damage.EntitySource entitySource)) {
            return; // Not entity damage (fall damage, fire, etc.)
        }

        // Get the attacker's entity reference
        final Ref<EntityStore> attackerRef = (Ref<EntityStore>) entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }
        
        // Get the target entity reference (the one being damaged)
        final Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Check if attacker is a player (not an NPC/mob)
        final Player attackerPlayer = (Player) store.getComponent(
                (Ref) attackerRef,
                Player.getComponentType()
        );
        if (attackerPlayer == null) {
            return; // Attacker is not a player
        }

        // Get the PlayerRef for notifications and inventory access
        final PlayerRef playerRef = (PlayerRef) store.getComponent(
                (Ref) attackerRef,
                PlayerRef.getComponentType()
        );
        if (playerRef == null) {
            return;
        }

        // Get the player's inventory
        final Inventory inventory = attackerPlayer.getInventory();
        if (inventory == null) {
            return;
        }

        // Get the weapon they're holding (not final - may be updated after idle flush)
        ItemStack weapon = inventory.getActiveHotbarItem();
        if (weapon == null || !this.itemExpService.canGainXp(weapon)) {
            return; // No weapon or weapon can't gain XP
        }
        
        // Get weapon level for effects
        final int weaponLevel = this.itemExpService.getItemLevel(weapon);
        
        // ==================== APPLY WEAPON EFFECTS ====================
        // Apply effects BEFORE awarding XP (bonus damage, life leech, etc.)
        if (weaponLevel > 1) {
            this.applyWeaponEffects(
                    damage,
                    targetRef,
                    attackerRef,
                    playerRef,
                    weapon,
                    weaponLevel,
                    inventory,
                    store,
                    commandBuffer
            );
        }
        
        // ==================== AWARD XP (with combat idle flush) ====================
        // Calculate XP to award based on original damage dealt
        final float damageDealt = damage.getAmount();
        final double xpGained = this.itemExpService.calculateXpFromDamage(damageDealt);

        if (xpGained <= 0) {
            return;
        }

        // Get slot and check combat idle timing
        final byte activeSlot = inventory.getActiveHotbarSlot();
        final String playerSlotKey = this.itemExpService.getPendingXpKey(playerRef, activeSlot);
        final long now = System.currentTimeMillis();
        final Long lastDamage = this.lastDamageTime.get(playerSlotKey);
        final boolean wasIdle = lastDamage != null && (now - lastDamage) >= COMBAT_IDLE_FLUSH_MS;
        
        // Update last damage time
        this.lastDamageTime.put(playerSlotKey, now);
        
        // If we were idle (combat ended), flush any pending XP first
        if (wasIdle) {
            final double pendingXp = this.itemExpService.getPendingXp(playerRef, activeSlot);
            if (pendingXp > 0) {
                this.flushPendingXpAndSwap(playerRef, attackerRef, inventory, store, activeSlot, weapon, false);
                // Get the updated weapon after flush
                weapon = inventory.getActiveHotbarItem();
            }
        }
        
        // Check if this XP gain would cause a level up
        final double currentXp = this.itemExpService.getTotalXpWithPending(weapon, playerRef, activeSlot);
        final int currentLevel = this.itemExpService.getItemLevel(weapon);
        final double totalXpAfter = currentXp + xpGained;
        final int levelAfter = this.itemExpService.calculateLevelFromXp(totalXpAfter);
        final boolean wouldLevelUp = levelAfter > currentLevel;
        
        // Cache the XP
        this.itemExpService.addPendingXp(playerRef, activeSlot, xpGained);
        
        // If level up, check if we're in rapid-fire combat - if so, delay the level up
        if (wouldLevelUp) {
            // Check if hits are coming very fast (rapid-fire, like an ultimate)
            // If the previous hit was within LEVEL_UP_DELAY_THRESHOLD_MS, delay the level up
            final boolean isRapidFire = lastDamage != null && (now - lastDamage) < LEVEL_UP_DELAY_THRESHOLD_MS;
            
            if (isRapidFire) {
                // Delay level up - rapid hits indicate an ultimate or charged attack in progress
                final double pendingTotal = this.itemExpService.getPendingXp(playerRef, activeSlot);
                if (this.config.isDebug()) {
                    System.out.println(String.format(
                            "[ItemExp] +%.2f XP (pending: %.2f) | LEVEL UP QUEUED %d->%d (rapid-fire %dms)",
                            xpGained, pendingTotal, currentLevel, levelAfter, (now - lastDamage)
                    ));
                }
                // Send XP notification even when delaying level up
                if (this.config.isShowXpNotifications() && xpGained >= this.config.getMinXpForNotification()) {
                    ItemExpNotifications.sendXpGainNotification(playerRef, xpGained, weapon, this.itemExpService);
                }
                // Don't do the swap - just cache and wait for a gap in combat
                return;
            }
            
            this.flushPendingXpAndSwap(playerRef, attackerRef, inventory, store, activeSlot, weapon, true);
            final ItemStack updatedWeapon = inventory.getActiveHotbarItem();
            
            // Send level up notifications
            ItemExpNotifications.sendLevelUpNotification(playerRef, updatedWeapon, levelAfter);
            
            final int pendingEmbues = this.itemExpService.getPendingEmbues(updatedWeapon);
            if (pendingEmbues > 0) {
                ItemExpNotifications.sendEmbueAvailableNotification(playerRef, pendingEmbues);
            }
            
            if (this.config.isDebug()) {
                final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
                System.out.println(String.format(
                        "[ItemExp] Level up! %d -> %d | Effects: %s",
                        currentLevel, levelAfter,
                        effectsService.getEffectsSummary(updatedWeapon)
                ));
            }
            
            // LEVEL UP BONUS: Max SignatureEnergy (Q meter)
            final World world = ((EntityStore) store.getExternalData()).getWorld();
            final Ref<EntityStore> finalAttackerRef = attackerRef;
            world.execute(() -> {
                this.maximizeSignatureEnergy(finalAttackerRef, store);
                if (this.config.isDebug()) {
                    System.out.println(String.format(
                            "[EOO] Level up bonus applied! SignatureEnergy: max=%.0f, current=%.0f",
                            this.getSignatureEnergyMax(finalAttackerRef, store),
                            this.getSignatureEnergy(finalAttackerRef, store)
                    ));
                }
            });
            
            return; // Level up handled, we're done
        }
        
        // No level up - just log if debug
        if (this.config.isDebug()) {
            final double pendingTotal = this.itemExpService.getPendingXp(playerRef, activeSlot);
            System.out.println(String.format(
                    "[ItemExp] Cached %.2f XP (pending total: %.2f, will flush after %dms idle)",
                    xpGained, pendingTotal, COMBAT_IDLE_FLUSH_MS
            ));
        }
        
        // Send XP notification if enabled
        if (this.config.isShowXpNotifications() && xpGained >= this.config.getMinXpForNotification()) {
            ItemExpNotifications.sendXpGainNotification(playerRef, xpGained, weapon, this.itemExpService);
        }
    }
    
    /**
     * Flush pending XP and swap the weapon in the hotbar.
     * 
     * @param isLevelUp If true, apply level-up bonuses (durability restore)
     */
    private void flushPendingXpAndSwap(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final Ref<EntityStore> attackerRef,
            @Nonnull final Inventory inventory,
            @Nonnull final Store<EntityStore> store,
            final byte slot,
            @Nonnull ItemStack weapon,
            final boolean isLevelUp
    ) {
        // Capture level BEFORE flushing XP (so we know which milestone levels were crossed)
        final int levelBefore = this.itemExpService.getItemLevel(weapon);
        final double pendingXp = this.itemExpService.getPendingXp(playerRef, slot);
        
        if (this.config.isDebug()) {
            final double xpBefore = this.itemExpService.getItemXp(weapon);
            System.out.println(String.format(
                    "[EOO] flushPendingXpAndSwap: BEFORE flush - Level=%d, StoredXP=%.2f, PendingXP=%.2f",
                    levelBefore, xpBefore, pendingXp
            ));
        }
        
        // Flush pending XP to the weapon
        weapon = this.itemExpService.flushPendingXp(weapon, playerRef, slot);
        
        // Get the new level AFTER flushing
        final int levelAfter = this.itemExpService.getItemLevel(weapon);
        
        if (this.config.isDebug()) {
            final double xpAfter = this.itemExpService.getItemXp(weapon);
            System.out.println(String.format(
                    "[EOO] flushPendingXpAndSwap: AFTER flush - Level=%d, StoredXP=%.2f, isLevelUp=%s",
                    levelAfter, xpAfter, isLevelUp
            ));
        }
        
        // Effects are static - only change when player selects upgrade (no auto-update)
        weapon = this.itemExpService.updateWeaponEffects(weapon, levelAfter);
        
        // Every level crossed gives 1 pending embue (Vampire Survivors style)
        weapon = this.itemExpService.addPendingEmbues(weapon, levelAfter - levelBefore);
        
        if (this.config.isDebug()) {
            System.out.println(String.format("[EOO] After adding embues: pendingEmbues=%d", this.itemExpService.getPendingEmbues(weapon)));
        }
        
        // Apply level-up bonuses
        if (isLevelUp) {
            weapon = weapon.withDurability(weapon.getMaxDurability());
        }
        
        // Swap the weapon, preserving SignatureEnergy
        this.swapWeaponPreservingSignature(attackerRef, store, inventory, slot, weapon);
        
        if (isLevelUp && this.config.isDebug()) {
            System.out.println("[EOO] Level up: Durability restored to max!");
        }
    }
    
    /**
     * Apply weapon effects when dealing damage.
     */
    @SuppressWarnings("deprecation")
    private void applyWeaponEffects(
            @Nonnull final Damage damage,
            @Nonnull final Ref<EntityStore> targetRef,
            @Nonnull final Ref<EntityStore> attackerRef,
            @Nonnull final PlayerRef playerRef,
            @Nonnull final ItemStack weapon,
            final int weaponLevel,
            @Nonnull final Inventory inventory,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer
    ) {
        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        
        // Determine weapon category from damage cause and weapon ID
        final WeaponCategory category = WeaponCategoryUtil.determineCategory(damage, weapon);
        
        // Build the effect context
        final EffectContext context = EffectContext.builder()
                .originalDamage(damage)
                .targetRef(targetRef)
                .attackerRef(attackerRef)
                .attackerPlayerRef(playerRef)
                .weapon(weapon)
                .weaponLevel(weaponLevel)
                .weaponCategory(category)
                .store(store)
                .commandBuffer(commandBuffer)
                .build();
        
        // Apply all on-damage effects (filtered by category)
        effectsService.applyOnDamageEffects(context);
        
        // Check if durability save was triggered - restore durability preemptively
        // The durability loss system will subtract, but we add first, so net = 0
        final Boolean shouldRestoreDurability = damage.getMetaStore().getIfPresentMetaObject(
                DurabilitySaveRestoreSystem.RESTORE_DURABILITY
        );
        
        if (shouldRestoreDurability != null && shouldRestoreDurability) {
            // Only restore if this damage cause would lose durability
            if (damage.getCause() != null && damage.getCause().isDurabilityLoss()) {
                this.restoreWeaponDurability(weapon, inventory, playerRef, attackerRef, store);
            }
        }
    }
    
    /**
     * Restore durability to a weapon by adding the amount that would be lost.
     * Called when DURABILITY_SAVE effect triggers.
     * We add durability BEFORE the system subtracts it, so net effect is 0.
     * 
     * Since we're replacing the weapon anyway, we also flush any pending XP.
     * We also preserve and restore SignatureEnergy to avoid resetting the Q meter.
     */
    private void restoreWeaponDurability(
            @Nonnull final ItemStack weapon,
            @Nonnull final Inventory inventory,
            @Nonnull final PlayerRef playerRef,
            @Nonnull final Ref<EntityStore> attackerRef,
            @Nonnull final Store<EntityStore> store
    ) {
        final var itemConfig = weapon.getItem();
        if (itemConfig == null || itemConfig.getWeapon() == null) {
            return;
        }
        
        // Get how much durability would be lost
        final double durabilityToRestore = itemConfig.getDurabilityLossOnHit();
        if (durabilityToRestore <= 0) {
            return;
        }
        
        // Get the hotbar slot
        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == -1) {
            return;
        }
        
        // Create a new item with added durability (positive = add)
        // This counteracts the durability loss that will happen later
        final ItemStack updatedWeapon = weapon.withIncreasedDurability(durabilityToRestore);
        
        // Swap the weapon while preserving SignatureEnergy
        this.swapWeaponPreservingSignature(attackerRef, store, inventory, activeSlot, updatedWeapon);
    }
    
    // ==================== SIGNATURE ENERGY HELPERS ====================
    
    /**
     * Get the current SignatureEnergy value for an entity.
     * Returns -1 if the stat is not found or entity has no stat map.
     */
    @SuppressWarnings("unchecked")
    private float getSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        final int signatureEnergyIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");
        if (signatureEnergyIndex == Integer.MIN_VALUE) {
            return -1f;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            return -1f;
        }
        
        final var statValue = statMap.get(signatureEnergyIndex);
        return statValue != null ? statValue.get() : -1f;
    }
    
    /**
     * Get the MAX SignatureEnergy value for an entity.
     * Returns -1 if the stat is not found or entity has no stat map.
     */
    @SuppressWarnings("unchecked")
    private float getSignatureEnergyMax(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        final int signatureEnergyIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");
        if (signatureEnergyIndex == Integer.MIN_VALUE) {
            return -1f;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap == null) {
            return -1f;
        }
        
        final var statValue = statMap.get(signatureEnergyIndex);
        return statValue != null ? statValue.getMax() : -1f;
    }
    
    /**
     * Set the SignatureEnergy value for an entity.
     */
    @SuppressWarnings("unchecked")
    private void setSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            final float value
    ) {
        final int signatureEnergyIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");
        if (signatureEnergyIndex == Integer.MIN_VALUE) {
            return;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap != null) {
            statMap.setStatValue(signatureEnergyIndex, value);
        }
    }
    
    /**
     * Maximize the SignatureEnergy stat for an entity (fill Q meter to 100%).
     */
    @SuppressWarnings("unchecked")
    private void maximizeSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        final int signatureEnergyIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");
        if (signatureEnergyIndex == Integer.MIN_VALUE) {
            return;
        }
        
        final EntityStatMap statMap = (EntityStatMap) store.getComponent(
                (Ref) entityRef,
                EntityStatMap.getComponentType()
        );
        
        if (statMap != null) {
            statMap.maximizeStatValue(signatureEnergyIndex);
        }
    }
    
    /**
     * Swap a weapon in the hotbar while preserving SignatureEnergy.
     * 
     * When items are swapped in the hotbar, the game resets SignatureEnergy to 0
     * (via EntityStatsToClear in the weapon config). This method captures the
     * current value before the swap, then schedules restore on the world thread.
     */
    private void swapWeaponPreservingSignature(
            @Nonnull final Ref<EntityStore> playerRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final byte slot,
            @Nonnull final ItemStack newWeapon
    ) {
        // Save current SignatureEnergy before swap
        final float savedEnergy = this.getSignatureEnergy(playerRef, store);
        
        // Do the swap
        final ItemContainer hotbar = inventory.getHotbar();
        hotbar.setItemStackForSlot((short) slot, newWeapon);
        
        // Restore SignatureEnergy on the world thread (for proper client sync)
        if (savedEnergy >= 0) {
            final World world = ((EntityStore) store.getExternalData()).getWorld();
            world.execute(() -> {
                this.setSignatureEnergy(playerRef, store, savedEnergy);
            });
        }
    }
    
    /**
     * Query that determines which entities this system processes.
     * Query.any() means all entities - we filter inside handle().
     */
    @Nullable
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
