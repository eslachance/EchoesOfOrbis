package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Handles UseBlockEvent.Pre: when the player uses a block (e.g. F on crop with sickle), awards tool XP
 * if the block is harvestable and the held item is a tool. This path runs regardless of which interaction
 * chain handles the use (item-specific vs global), so sickle-on-crop harvest gets XP here.
 */
public final class ToolUseBlockEventSystem extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final double TOOL_XP_PER_HARVEST_DROP = 2.0;

    private final ItemExpService itemExpService;
    private final HudDisplaySystem hudDisplaySystem;

    public ToolUseBlockEventSystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final HudDisplaySystem hudDisplaySystem
    ) {
        super(UseBlockEvent.Pre.class);
        this.itemExpService = itemExpService;
        this.hudDisplaySystem = hudDisplaySystem;
    }

    @Override
    public void handle(
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final UseBlockEvent.Pre event
    ) {
        final BlockType blockType = event.getBlockType();
        if (blockType == null || !BlockHarvestUtils.shouldPickupByInteraction(blockType)) {
            return;
        }
        final InteractionContext context = event.getContext();
        if (context == null) {
            return;
        }
        final ItemStack tool = context.getHeldItem();
        if (tool == null || tool.isEmpty() || tool.getItem() == null || tool.getItem().getTool() == null) {
            return;
        }
        if (itemExpService == null || !itemExpService.canGainXp(tool)) {
            return;
        }
        final BlockGathering gathering = blockType.getGathering();
        if (gathering == null || gathering.getHarvest() == null) {
            return;
        }
        final HarvestingDropType harvest = gathering.getHarvest();
        final String itemId = harvest.getItemId();
        final String dropListId = harvest.getDropListId();
        final List<ItemStack> drops = BlockHarvestUtils.getDrops(blockType, 1, itemId, dropListId);
        int vanillaDropCount = 0;
        if (drops != null) {
            for (ItemStack stack : drops) {
                if (stack != null && !stack.isEmpty()) {
                    vanillaDropCount += stack.getQuantity();
                }
            }
        }
        final double xpToAdd = TOOL_XP_PER_HARVEST_DROP * Math.max(1, vanillaDropCount);

        final Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        final PlayerRef playerRef = (PlayerRef) store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        final Player player = (Player) store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        final byte activeSlot = context.getHeldItemSlot();
        if (activeSlot < 0 || activeSlot > 8) {
            return;
        }

        final int currentToolLevel = itemExpService.getItemLevel(tool);
        itemExpService.addPendingXp(playerRef, activeSlot, xpToAdd);
        // Log once so we know UseBlockEvent.Pre is the path for sickle-on-crop
        System.out.println("[EOO] ToolUseBlockEventSystem: awarded " + xpToAdd + " XP for tool use on harvestable block " + blockType.getId());
        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool != null && !currentTool.isEmpty() && currentTool.getItem() != null && currentTool.getItem().getTool() != null) {
            final double totalXpAfter = itemExpService.getTotalXpWithPending(currentTool, playerRef, activeSlot);
            final int levelAfter = itemExpService.calculateLevelFromXp(totalXpAfter);
            if (levelAfter > currentToolLevel) {
                ItemStack flushedTool = itemExpService.flushPendingXp(currentTool, playerRef, activeSlot);
                flushedTool = itemExpService.updateWeaponEffects(flushedTool, levelAfter);
                flushedTool = itemExpService.addPendingEmbues(flushedTool, levelAfter - currentToolLevel);
                WeaponSwapUtil.swapWeaponAndMaximizeSignature(entityRef, store, inventory, activeSlot, flushedTool);
                if (hudDisplaySystem != null) {
                    hudDisplaySystem.updateHudForPlayer(playerRef, flushedTool, activeSlot);
                }
                ItemExpNotifications.sendLevelUpNotificationWithIcon(playerRef, flushedTool, levelAfter, itemExpService);
            } else if (hudDisplaySystem != null) {
                hudDisplaySystem.updateHudForPlayer(playerRef, currentTool, activeSlot);
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
