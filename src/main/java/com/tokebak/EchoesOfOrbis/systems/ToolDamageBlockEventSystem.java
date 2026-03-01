package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.EooLogger;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Handles DamageBlockEvent: when the player left-clicks (swipes) a harvestable block with a tool
 * (e.g. sickle on wheat) and this hit will destroy the block, awards tool XP. Harvestable crops
 * are often "state change" harvests (no BreakBlockEvent), so this is the only hook that runs.
 */
public final class ToolDamageBlockEventSystem extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final double TOOL_XP_PER_HARVEST_DROP = 2.0;

    private final ItemExpService itemExpService;
    private final HudDisplaySystem hudDisplaySystem;

    public ToolDamageBlockEventSystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final HudDisplaySystem hudDisplaySystem
    ) {
        super(DamageBlockEvent.class);
        this.itemExpService = itemExpService;
        this.hudDisplaySystem = hudDisplaySystem;
    }

    @Override
    public void handle(
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final DamageBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }
        // Only award when this hit will destroy/harvest the block (avoid XP per damage tick)
        final float currentHealth = event.getCurrentDamage();
        final float damage = event.getDamage();
        if (currentHealth > damage) {
            return;
        }
        final BlockType blockType = event.getBlockType();
        if (blockType == null) {
            return;
        }
        if (!BlockHarvestUtils.shouldPickupByInteraction(blockType)) {
            return;
        }
        final ItemStack tool = event.getItemInHand();
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
        final List<ItemStack> drops = BlockHarvestUtils.getDrops(blockType, 1, harvest.getItemId(), harvest.getDropListId());
        int vanillaDropCount = 0;
        if (drops != null) {
            for (ItemStack stack : drops) {
                if (stack != null && !stack.isEmpty()) {
                    vanillaDropCount += stack.getQuantity();
                }
            }
        }
        final double xpToAdd = TOOL_XP_PER_HARVEST_DROP * Math.max(1, vanillaDropCount);

        final Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        if (entityRef == null || !entityRef.isValid()) {
            return;
        }
        final Player player = (Player) store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        final PlayerRef playerRef = (PlayerRef) store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot < 0 || activeSlot > 8) {
            return;
        }

        final int currentToolLevel = itemExpService.getItemLevel(tool);
        itemExpService.addPendingXp(playerRef, activeSlot, xpToAdd);
        EooLogger.debug("ToolDamageBlockEventSystem: awarded %.0f XP for sickle harvest on %s", xpToAdd, blockType.getId());
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
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
