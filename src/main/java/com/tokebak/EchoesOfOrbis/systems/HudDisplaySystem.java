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
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
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
 */
public class HudDisplaySystem extends EntityTickingSystem<EntityStore> {
    
    private final ItemExpService itemExpService;
    
    /**
     * Tracks the last known active hotbar slot per player UUID.
     * Used to detect when the slot has changed.
     */
    private final Map<UUID, Byte> lastActiveSlot = new ConcurrentHashMap<>();
    
    public HudDisplaySystem(@Nonnull final ItemExpService itemExpService) {
        this.itemExpService = itemExpService;
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
        
        // Get current active slot
        final byte currentSlot = inventory.getActiveHotbarSlot();
        
        // Get last known slot (or initialize if first time)
        final Byte lastSlotObj = this.lastActiveSlot.get(playerUuid);
        if (lastSlotObj == null) {
            // First time seeing this player - just record their current slot
            this.lastActiveSlot.put(playerUuid, currentSlot);
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
        
        // Update HUD based on whether the weapon has effects
        if (hasWeaponWithEffects) {
            // Show the status HUD if not already showing it
            if (!(player.getHudManager().getCustomHud() instanceof EOO_Status_Hud)) {
                player.getHudManager().setCustomHud(playerRef, new EOO_Status_Hud(playerRef));
            }
        } else {
            // Hide the HUD (show blank HUD) if not already hidden
            if (!(player.getHudManager().getCustomHud() instanceof BlankHud)) {
                player.getHudManager().setCustomHud(playerRef, new BlankHud(playerRef));
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
     * Clean up tracking data when a player disconnects.
     */
    public void cleanupPlayer(@Nonnull final UUID playerUuid) {
        this.lastActiveSlot.remove(playerUuid);
    }
}
