package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.EooLogger;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;

/**
 * Handles {@link PlayerInteractEvent}: when the player uses a tool (e.g. shears) on an entity
 * (e.g. sheep for wool/poop), awards tool XP and runs the same level-up flow as block breaks.
 * Left-click with shears = Primary interaction; this is not a block break or BreakBlockEvent.
 */
public final class ToolEntityInteractHandler {

    private static final double TOOL_XP_PER_ENTITY_USE = 2.0;

    private final ItemExpService itemExpService;
    private final HudDisplaySystem hudDisplaySystem;

    public ToolEntityInteractHandler(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final HudDisplaySystem hudDisplaySystem
    ) {
        this.itemExpService = itemExpService;
        this.hudDisplaySystem = hudDisplaySystem;
    }

    public void onPlayerInteract(@Nonnull final PlayerInteractEvent event) {
        final InteractionType actionType = event.getActionType();
        final boolean hasTargetEntity = event.getTargetEntity() != null;
        final ItemStack itemInHand = event.getItemInHand();
        final String itemId = itemInHand != null && itemInHand.getItem() != null ? itemInHand.getItem().getId() : "null";
        EooLogger.debug("ToolEntity PlayerInteractEvent: actionType=%s, targetEntity=%s, itemInHand=%s", actionType, hasTargetEntity, itemId);

        if (event.isCancelled()) {
            EooLogger.debug("ToolEntity SKIP: event cancelled");
            return;
        }
        if (actionType != InteractionType.Primary) {
            EooLogger.debug("ToolEntity SKIP: not Primary (got %s)", actionType);
            return;
        }
        if (!hasTargetEntity) {
            EooLogger.debug("ToolEntity SKIP: no target entity");
            return;
        }

        final ItemStack tool = event.getItemInHand();
        if (tool == null || tool.isEmpty()) {
            EooLogger.debug("ToolEntity SKIP: no item in hand or empty");
            return;
        }
        if (!itemExpService.canGainXp(tool)) {
            EooLogger.debug("ToolEntity SKIP: canGainXp=false for %s", itemId);
            return;
        }
        if (tool.getItem() == null || tool.getItem().getTool() == null) {
            EooLogger.debug("ToolEntity SKIP: not a tool (getTool=null) for %s", itemId);
            return;
        }

        final Ref<EntityStore> entityRef = event.getPlayerRef();
        if (entityRef == null || !entityRef.isValid()) {
            EooLogger.debug("ToolEntity SKIP: playerRef null or invalid");
            return;
        }

        final Player player = event.getPlayer();
        if (player == null) {
            EooLogger.debug("ToolEntity SKIP: player null");
            return;
        }

        final Store<EntityStore> store = entityRef.getStore();
        final PlayerRef playerRef = (PlayerRef) store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            EooLogger.debug("ToolEntity SKIP: PlayerRef component null");
            return;
        }

        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            EooLogger.debug("ToolEntity SKIP: inventory null");
            return;
        }

        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == -1) {
            EooLogger.debug("ToolEntity SKIP: activeSlot=-1");
            return;
        }

        final int currentToolLevel = itemExpService.getItemLevel(tool);
        EooLogger.debug("ToolEntity AWARDING %.0f XP for tool %s on entity (slot=%d, level=%d)", TOOL_XP_PER_ENTITY_USE, itemId, activeSlot, currentToolLevel);

        itemExpService.addPendingXp(playerRef, activeSlot, TOOL_XP_PER_ENTITY_USE);

        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool == null || currentTool.isEmpty() || currentTool.getItem() == null || currentTool.getItem().getTool() == null) {
            EooLogger.warn("ToolEntity: tool null or not-tool after addPendingXp (slot may have changed)");
            return;
        }

        final double totalXpAfter = itemExpService.getTotalXpWithPending(currentTool, playerRef, activeSlot);
        final int levelAfter = itemExpService.calculateLevelFromXp(totalXpAfter);
        if (levelAfter > currentToolLevel) {
            EooLogger.debug("ToolEntity LEVEL UP %d -> %d", currentToolLevel, levelAfter);
            ItemStack flushedTool = itemExpService.flushPendingXp(currentTool, playerRef, activeSlot);
            flushedTool = itemExpService.updateWeaponEffects(flushedTool, levelAfter);
            flushedTool = itemExpService.addPendingEmbues(flushedTool, levelAfter - currentToolLevel);
            WeaponSwapUtil.swapWeaponAndMaximizeSignature(entityRef, store, inventory, activeSlot, flushedTool);
            hudDisplaySystem.updateHudForPlayer(playerRef, flushedTool, activeSlot);
            ItemExpNotifications.sendLevelUpNotificationWithIcon(playerRef, flushedTool, levelAfter, itemExpService);
        } else {
            hudDisplaySystem.updateHudForPlayer(playerRef, currentTool, activeSlot);
        }
        EooLogger.debug("ToolEntity Done: XP added, HUD updated");
    }

    /**
     * Called when {@link PlayerMouseButtonEvent} fires with a tool and target entity (e.g. left-click shears on sheep).
     * MouseButtonEvent is the raw click; the game may use this instead of PlayerInteractEvent for entity interaction.
     */
    public void onMouseButtonEntity(@Nonnull final PlayerMouseButtonEvent event) {
        if (event.isCancelled()) {
            return;
        }
        final Ref<EntityStore> entityRef = event.getPlayerRef();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final Store<EntityStore> store = entityRef.getStore();
        final PlayerRef playerRef = (PlayerRef) store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot == -1) {
            return;
        }
        final ItemStack tool = inventory.getActiveHotbarItem();
        if (tool == null || tool.isEmpty() || tool.getItem() != event.getItemInHand() || !itemExpService.canGainXp(tool) || tool.getItem().getTool() == null) {
            return;
        }
        final int currentToolLevel = itemExpService.getItemLevel(tool);
        EooLogger.debug("ToolEntity MouseButton: AWARDING %.0f XP for tool on entity (slot=%d)", TOOL_XP_PER_ENTITY_USE, activeSlot);
        itemExpService.addPendingXp(playerRef, activeSlot, TOOL_XP_PER_ENTITY_USE);
        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool == null || currentTool.isEmpty() || currentTool.getItem() == null || currentTool.getItem().getTool() == null) {
            return;
        }
        final double totalXpAfter = itemExpService.getTotalXpWithPending(currentTool, playerRef, activeSlot);
        final int levelAfter = itemExpService.calculateLevelFromXp(totalXpAfter);
        if (levelAfter > currentToolLevel) {
            ItemStack flushedTool = itemExpService.flushPendingXp(currentTool, playerRef, activeSlot);
            flushedTool = itemExpService.updateWeaponEffects(flushedTool, levelAfter);
            flushedTool = itemExpService.addPendingEmbues(flushedTool, levelAfter - currentToolLevel);
            WeaponSwapUtil.swapWeaponAndMaximizeSignature(entityRef, store, inventory, activeSlot, flushedTool);
            hudDisplaySystem.updateHudForPlayer(playerRef, flushedTool, activeSlot);
            ItemExpNotifications.sendLevelUpNotificationWithIcon(playerRef, flushedTool, levelAfter, itemExpService);
        } else {
            hudDisplaySystem.updateHudForPlayer(playerRef, currentTool, activeSlot);
        }
    }
}
