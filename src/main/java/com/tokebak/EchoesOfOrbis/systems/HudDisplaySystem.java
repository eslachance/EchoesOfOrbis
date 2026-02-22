package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.PlayerStatModifierService;
import com.tokebak.EchoesOfOrbis.services.RingHealthRegenEffectApplier;
import com.tokebak.EchoesOfOrbis.ui.BlankHud;
import com.tokebak.EchoesOfOrbis.ui.EOO_Status_Hud;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System that manages the EOO Status HUD display based on active weapon.
 * Shows the HUD when the player switches to a weapon with effects, hides it otherwise.
 * Also applies the Healing Totem heal effect periodically to players with RING_HEALTH_REGEN (like standing in the totem).
 */
public class HudDisplaySystem extends EntityTickingSystem<EntityStore> {

    /** Interval (seconds) between applying Healing_Totem_Heal when player has RING_HEALTH_REGEN. Matches totem feel. */
    private static final float RING_HEAL_INTERVAL = 1f;

    private final ItemExpService itemExpService;
    private final BaubleContainerService baubleContainerService;

    /**
     * Tracks the last known active hotbar slot per player UUID.
     * Used to detect when the slot has changed.
     */
    private final Map<UUID, Byte> lastActiveSlot = new ConcurrentHashMap<>();

    /**
     * Tracks the active EOO_Status_Hud instance per player UUID.
     * Used to update the HUD when XP changes without switching weapons.
     */
    private final Map<UUID, EOO_Status_Hud> activeHuds = new ConcurrentHashMap<>();

    /**
     * Per-player accumulator (seconds) for applying ring heal effect. When >= RING_HEAL_INTERVAL, apply and reset.
     */
    private final Map<UUID, Float> ringHealAccumulator = new ConcurrentHashMap<>();

    public HudDisplaySystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final BaubleContainerService baubleContainerService
    ) {
        this.itemExpService = itemExpService;
        this.baubleContainerService = baubleContainerService;
    }
    
    /**
     * Query that determines which entities this system ticks for.
     * We want to tick for all Player entities.
     */
    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
    
    /**
     * Called every tick for each player entity.
     * Checks if the active hotbar slot has changed and updates HUD display accordingly.
     */
    @Override
    public void tick(
            final float dt,
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer
    ) {
        // Get the entity reference
        final Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (!entityRef.isValid()) {
            return;
        }
        
        // Get Player component
        final Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            return;
        }
        
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        
        final UUIDComponent uuidComponent = store.getComponent(
                entityRef,
                UUIDComponent.getComponentType()
        );
        if (uuidComponent == null) {
            return;
        }
        final UUID playerUuid = uuidComponent.getUuid();

        // Apply Healing Totem heal effect periodically when player has RING_HEALTH_REGEN (same effect as deployable totem)
        final PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef != null) {
            ItemContainer bauble = this.baubleContainerService.getOrCreate(playerRef);
            double regenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.itemExpService.getEffectsService());
            if (regenBonus > 0) {
                float acc = this.ringHealAccumulator.getOrDefault(playerUuid, 0f) + dt;
                if (acc >= RING_HEAL_INTERVAL) {
                    RingHealthRegenEffectApplier.applyIfHasRing(entityRef, store, bauble, this.itemExpService.getEffectsService());
                    acc = 0f;
                }
                this.ringHealAccumulator.put(playerUuid, acc);
            } else {
                this.ringHealAccumulator.remove(playerUuid);
            }
        }

        // Get current active slot
        final byte currentSlot = inventory.getActiveHotbarSlot();

        // Get last known slot (or initialize if first time)
        final Byte lastSlotObj = this.lastActiveSlot.get(playerUuid);
        if (lastSlotObj == null) {
            // First time seeing this player (e.g. just joined) - record slot and show HUD if they have a weapon equipped
            this.lastActiveSlot.put(playerUuid, currentSlot);
            this.handleSlotChange(entityRef, store, player, inventory, currentSlot);
            return;
        }
        
        final byte previousSlot = lastSlotObj;
        
        // Check if slot changed
        if (currentSlot == previousSlot) {
            // No slot change - nothing to do
            return;
        }
        
        // Slot changed! Update tracking
        this.lastActiveSlot.put(playerUuid, currentSlot);
        
        // Handle the slot change
        this.handleSlotChange(entityRef, store, player, inventory, currentSlot);
    }
    
    /**
     * Handle a detected hotbar slot change.
     * Shows HUD if the new weapon has effects, hides it otherwise.
     */
    private void handleSlotChange(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Player player,
            @Nonnull final Inventory inventory,
            final byte currentSlot
    ) {
        // Get the item in the new slot
        final ItemContainer hotbar = inventory.getHotbar();
        final ItemStack currentItem = hotbar.getItemStack((short) currentSlot);
        
        // Get player ref component for HUD operations
        final PlayerRef playerRef = store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        
        // Check if the current item is a trackable weapon with effects
        final boolean hasWeaponWithEffects = this.hasEffects(currentItem);
        
        // Get player UUID for tracking
        final UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());
        final UUID playerUuid = uuidComponent != null ? uuidComponent.getUuid() : null;
        
        // Update HUD based on whether the weapon has effects
        if (hasWeaponWithEffects) {
            // Show the status HUD if not already showing it, or update if already showing
            var currentHud = player.getHudManager().getCustomHud();
            if (currentHud instanceof EOO_Status_Hud) {
                // Already showing - just update with new weapon data
                ((EOO_Status_Hud) currentHud).updateWithWeapon(currentItem, currentSlot);
                // Track this HUD instance
                if (playerUuid != null) {
                    this.activeHuds.put(playerUuid, (EOO_Status_Hud) currentHud);
                }
            } else {
                // Create new HUD and update it with weapon data
                EOO_Status_Hud newHud = new EOO_Status_Hud(playerRef, this.itemExpService);
                player.getHudManager().setCustomHud(playerRef, newHud);
                newHud.updateWithWeapon(currentItem, currentSlot);
                // Track this HUD instance
                if (playerUuid != null) {
                    this.activeHuds.put(playerUuid, newHud);
                }
            }
        } else {
            // Hide the HUD (show blank HUD) if not already hidden
            if (!(player.getHudManager().getCustomHud() instanceof BlankHud)) {
                player.getHudManager().setCustomHud(playerRef, new BlankHud(playerRef));
            }
            // Remove from tracking
            if (playerUuid != null) {
                this.activeHuds.remove(playerUuid);
            }
        }
    }
    
    /**
     * Check if an item is a trackable weapon with effects.
     */
    private boolean hasEffects(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        
        // Check if it's a trackable weapon
        if (!this.itemExpService.canGainXp(item)) {
            return false;
        }
        
        // Check if it has any effects
        final String effectsSummary = this.itemExpService.getEffectsService().getEffectsSummary(item);
        return effectsSummary != null && !effectsSummary.isEmpty();
    }
    
    /**
     * Refresh the HUD for the player's currently selected hotbar slot.
     * Call this when the item in the active slot changes without the slot selection changing
     * (e.g. craft into selected slot, or move item from another inventory into selected slot).
     */
    public void refreshHudForCurrentSlot(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Player player
    ) {
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        final byte currentSlot = inventory.getActiveHotbarSlot();
        this.handleSlotChange(entityRef, store, player, inventory, currentSlot);
    }

    /**
     * Update the HUD for a specific player with their current weapon.
     * Call this when XP changes to refresh the display.
     * 
     * @param playerRef The player whose HUD should be updated
     * @param weapon The current weapon to display (should be the active hotbar item)
     * @param slot The hotbar slot of the weapon
     */
    public void updateHudForPlayer(@Nonnull final PlayerRef playerRef, @Nullable final ItemStack weapon, byte slot) {
        final UUID playerUuid = playerRef.getUuid();
        final EOO_Status_Hud hud = this.activeHuds.get(playerUuid);
        
        if (hud != null) {
            hud.updateWithWeapon(weapon, slot);
        }
    }
    
    /**
     * Clean up tracking data when a player disconnects.
     */
    public void cleanupPlayer(@Nonnull final UUID playerUuid) {
        this.lastActiveSlot.remove(playerUuid);
        this.activeHuds.remove(playerUuid);
        this.ringHealAccumulator.remove(playerUuid);
    }
}
