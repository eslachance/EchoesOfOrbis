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

        // Get the weapon they're holding
        final ItemStack weapon = inventory.getActiveHotbarItem();
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
        
        // ==================== AWARD XP ====================
        // Calculate XP to award based on original damage dealt
        final float damageDealt = damage.getAmount();
        final double xpGained = this.itemExpService.calculateXpFromDamage(damageDealt);

        if (xpGained <= 0) {
            return;
        }

        // Add XP to the weapon (this also updates effects on level up)
        final ItemExpService.LevelUpResult result = this.itemExpService.addXpToHeldWeapon(
                playerRef,
                inventory,
                xpGained
        );

        if (!result.isSuccess()) {
            return;
        }

        // Handle level up - need to swap weapon and manage SignatureEnergy
        ItemStack updatedWeapon;
        final byte activeSlot = inventory.getActiveHotbarSlot();
        
        if (result.needsSwap()) {
            // Level up occurred - get the updated weapon from result
            updatedWeapon = result.getUpdatedWeapon();
            
            // BONUS: Restore durability to max on level up
            updatedWeapon = updatedWeapon.withDurability(updatedWeapon.getMaxDurability());
            
            // Swap the weapon in hotbar (SignatureEnergy handled at end of method)
            this.swapWeaponOnLevelUp(
                    inventory,
                    result.getSlot(),
                    updatedWeapon
            );
            
            // Get the weapon from inventory for notifications (same as what we put in)
            updatedWeapon = inventory.getActiveHotbarItem();
        } else {
            // No level up - just get the current weapon
            updatedWeapon = inventory.getActiveHotbarItem();
        }

        // Debug: Log XP gain to console
        if (this.config.isDebug()) {
            // Get total XP including any pending (cached) XP
            final double totalXp = this.itemExpService.getTotalXpWithPending(updatedWeapon, playerRef, activeSlot);
            // Calculate remaining XP to next level (threshold - current)
            final double xpForNextLevel = this.itemExpService.getXpRequiredForLevel(result.getLevelAfter() + 1);
            final double xpRemaining = Math.max(0, xpForNextLevel - totalXp);
            System.out.println(String.format(
                    "[ItemExp] %s gained %.2f XP | Total: %.2f | Level: %d | XP remaining: %.2f",
                    updatedWeapon.getItemId(),
                    xpGained,
                    totalXp,
                    result.getLevelAfter(),
                    xpRemaining
            ));
        }

        // Send notifications if enabled
        if (this.config.isShowXpNotifications() && xpGained >= this.config.getMinXpForNotification()) {
            ItemExpNotifications.sendXpGainNotification(playerRef, xpGained, updatedWeapon, this.itemExpService);
        }

        // Check for level up
        if (result.didLevelUp()) {
            ItemExpNotifications.sendLevelUpNotification(playerRef, updatedWeapon, result.getLevelAfter());
            
            // Check for pending embues and notify player
            final int pendingEmbues = this.itemExpService.getPendingEmbues(updatedWeapon);
            if (pendingEmbues > 0) {
                ItemExpNotifications.sendEmbueAvailableNotification(playerRef, pendingEmbues);
            }
            
            // Log effect summary on level up
            final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
            System.out.println(String.format(
                    "[ItemExp] Level up! New effects: %s",
                    effectsService.getEffectsSummary(updatedWeapon)
            ));
            
            // LEVEL UP BONUS: Max SignatureEnergy (Q meter)
            // Schedule on world thread to ensure proper client sync
            final World world = ((EntityStore) store.getExternalData()).getWorld();
            final Ref<EntityStore> finalAttackerRef = attackerRef;
            world.execute(() -> {
                this.maximizeSignatureEnergy(finalAttackerRef, store);
                System.out.println(String.format(
                        "[EOO] Level up bonus applied! SignatureEnergy: max=%.0f, current=%.0f",
                        this.getSignatureEnergyMax(finalAttackerRef, store),
                        this.getSignatureEnergy(finalAttackerRef, store)
                ));
            });
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
        
        // Since we're replacing the weapon anyway, flush any pending XP first
        ItemStack updatedWeapon = this.itemExpService.flushPendingXp(weapon, playerRef, activeSlot);
        
        // Create a new item with added durability (positive = add)
        // This counteracts the durability loss that will happen later
        updatedWeapon = updatedWeapon.withIncreasedDurability(durabilityToRestore);
        
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
     * Swap a weapon on level up.
     * Just does the swap - SignatureEnergy handling is done separately at the
     * end of handle() to avoid race conditions with the hotbar change event.
     */
    private void swapWeaponOnLevelUp(
            @Nonnull final Inventory inventory,
            final byte slot,
            @Nonnull final ItemStack newWeapon
    ) {
        // Do the swap
        final ItemContainer hotbar = inventory.getHotbar();
        hotbar.setItemStackForSlot((short) slot, newWeapon);
        
        System.out.println("[EOO] Level up: Durability restored to max!");
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
