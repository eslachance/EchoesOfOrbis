package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.EooLogger;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * Handles UseBlockEvent.Pre: when the player uses a block (e.g. F on crop with sickle), awards tool XP
 * if the block is harvestable and the held item is a tool. This path runs regardless of which interaction
 * chain handles the use (item-specific vs global), so sickle-on-crop harvest gets XP here.
 */
public final class ToolUseBlockEventSystem extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private static final double TOOL_XP_PER_HARVEST_DROP = 2.0;
    private static final double DROP_BONUS_CAP = 0.50;

    private final ItemExpService itemExpService;
    private final WeaponEffectsService effectsService;
    private final HudDisplaySystem hudDisplaySystem;
    private final Random random = new Random();

    public ToolUseBlockEventSystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final WeaponEffectsService effectsService,
            @Nonnull final HudDisplaySystem hudDisplaySystem
    ) {
        super(UseBlockEvent.Pre.class);
        this.itemExpService = itemExpService;
        this.effectsService = effectsService;
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
        if (tool == null || tool.isEmpty() || !WeaponCategoryUtil.isTool(tool)) {
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
        EooLogger.debug("ToolUseBlockEventSystem: awarded %.0f XP for tool use on harvestable block %s", xpToAdd, blockType.getId());
        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool != null && !currentTool.isEmpty() && WeaponCategoryUtil.isTool(currentTool)) {
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
            } else {
                ItemStack flushedTool = itemExpService.flushPendingXp(currentTool, playerRef, activeSlot);
                inventory.getHotbar().setItemStackForSlot((short) activeSlot, flushedTool);
                if (hudDisplaySystem != null) {
                    hudDisplaySystem.updateHudForPlayer(playerRef, flushedTool, activeSlot);
                }
            }
        }

        // ---- Bonus drops (TOOL_DROP_BONUS) ----
        final int toolLevelForDrops = itemExpService.getItemLevel(
                currentTool != null && !currentTool.isEmpty() ? currentTool : tool);
        if (toolLevelForDrops > 1) {
            final ItemStack dropCheckTool = (currentTool != null && !currentTool.isEmpty()) ? currentTool : tool;
            double bonusPercent = 0;
            for (var inst : effectsService.getEffects(dropCheckTool)) {
                if (inst.getType() == WeaponEffectType.TOOL_DROP_BONUS) {
                    var def = effectsService.getDefinition(inst.getType());
                    if (def != null) {
                        bonusPercent = Math.min(DROP_BONUS_CAP, def.calculateValue(inst.getLevel()));
                        break;
                    }
                }
            }
            if (bonusPercent > 0 && random.nextDouble() < bonusPercent) {
                final BlockPosition blockPos = context.getTargetBlock();
                if (blockPos != null) {
                    final List<ItemStack> bonusStacks = BlockHarvestUtils.getDrops(blockType, 1, itemId, dropListId);
                    if (bonusStacks != null && !bonusStacks.isEmpty()) {
                        final Vector3d dropPos = new Vector3d(
                                blockPos.x + 0.5,
                                blockPos.y,
                                blockPos.z + 0.5
                        );
                        var holders = ItemComponent.generateItemDrops(store, bonusStacks, dropPos, Vector3f.ZERO);
                        for (var holder : holders) {
                            commandBuffer.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
                        }
                        EooLogger.debug("ToolUseBlockEventSystem: bonus drop spawned for %s", blockType.getId());
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
