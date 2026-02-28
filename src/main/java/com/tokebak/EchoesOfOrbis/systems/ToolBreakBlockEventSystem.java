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
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Handles BreakBlockEvent: tool durability save, tool XP, and bonus drops for tools with TOOL_DROP_BONUS.
 * Runs once per block broken (including shovel area and axe tree falls).
 */
public final class ToolBreakBlockEventSystem extends com.hypixel.hytale.component.system.EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final ItemExpService itemExpService;
    private final WeaponEffectsService effectsService;
    private final HudDisplaySystem hudDisplaySystem;
    private final Random random = new Random();

    private static final double DURABILITY_SAVE_CAP = 0.50;
    private static final double DROP_BONUS_CAP = 0.50;

    private static final double TOOL_XP_PER_BREAK = 2.0;
    /** Max connected support blocks to count (tree-fall); caps XP and avoids cost on huge structures. */
    private static final int MAX_CONNECTED_SUPPORT_BLOCKS = 64;

    public ToolBreakBlockEventSystem(
            @Nonnull final ItemExpService itemExpService,
            @Nonnull final WeaponEffectsService effectsService,
            @Nonnull final HudDisplaySystem hudDisplaySystem
    ) {
        super(BreakBlockEvent.class);
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
            @Nonnull final BreakBlockEvent event
    ) {
        if (event.isCancelled()) {
            return;
        }

        final ItemStack tool = event.getItemInHand();
        if (tool == null || tool.isEmpty() || !itemExpService.canGainXp(tool)) {
            return;
        }

        if (tool.getItem() == null || tool.getItem().getTool() == null) {
            return;
        }

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
        if (activeSlot == -1) {
            return;
        }

        final int toolLevel = itemExpService.getItemLevel(tool);
        final BlockType blockType = event.getBlockType();

        // ---- Durability save (tools) ----
        double saveChance = 0;
        for (var inst : effectsService.getEffects(tool)) {
            if (inst.getType() == WeaponEffectType.DURABILITY_SAVE && inst.getType().appliesTo(WeaponCategory.TOOL)) {
                var def = effectsService.getDefinition(inst.getType());
                if (def != null) {
                    saveChance = Math.min(DURABILITY_SAVE_CAP, def.calculateValue(inst.getLevel()));
                    break;
                }
            }
        }
        if (toolLevel > 1 && saveChance > 0) {
            if (random.nextDouble() < saveChance) {
                final double durabilityToRestore = BlockHarvestUtils.calculateDurabilityUse(tool.getItem(), blockType);
                if (durabilityToRestore > 0 && !tool.isUnbreakable()) {
                    final ItemStack updatedTool = tool.withIncreasedDurability(durabilityToRestore);
                    WeaponSwapUtil.swapWeaponPreservingSignature(entityRef, store, inventory, activeSlot, updatedTool);
                }
            }
        }

        // Re-read tool from inventory in case durability save swapped it
        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool == null || currentTool.isEmpty() || currentTool.getItem() == null || currentTool.getItem().getTool() == null) {
            return;
        }
        final int currentToolLevel = itemExpService.getItemLevel(currentTool);

        // ---- Tool XP ----
        double xpToAdd = TOOL_XP_PER_BREAK;
        if (blockType.getSupportDropType() != null) {
            final World world = ((EntityStore) store.getExternalData()).getWorld();
            final int originY = event.getTargetBlock().getY();
            final ConnectedSupportResult result = countConnectedSupportBlocks(
                    world,
                    event.getTargetBlock().getX(),
                    originY,
                    event.getTargetBlock().getZ(),
                    blockType,
                    MAX_CONNECTED_SUPPORT_BLOCKS
            );
            // Only award falling-block XP when there are no other supporting blocks at the same Y level.
            // BreakBlockEvent runs before the block is removed, so the block we broke is still there during BFS
            // and is counted in sameLevelCount. "Only block at this level" => sameLevelCount == 1 (not 0).
            if (result.sameLevelCount <= 1 && result.totalCount > 0) {
                xpToAdd += (result.totalCount - 1) * TOOL_XP_PER_BREAK; // -1: don't double-count the block we broke
            }
        }
        itemExpService.addPendingXp(playerRef, activeSlot, xpToAdd);
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

        // ---- Bonus drops (TOOL_DROP_BONUS) ----
        if (currentToolLevel > 1) {
            double bonusPercent = 0;
            for (var inst : effectsService.getEffects(currentTool)) {
                if (inst.getType() == WeaponEffectType.TOOL_DROP_BONUS) {
                    var def = effectsService.getDefinition(inst.getType());
                    if (def != null) {
                        bonusPercent = Math.min(DROP_BONUS_CAP, def.calculateValue(inst.getLevel()));
                        break;
                    }
                }
            }
            if (bonusPercent > 0) {
                spawnBonusDrops(event, blockType, bonusPercent, store, commandBuffer);
            }
        }
    }

    /**
     * Result of counting connected support blocks: total count and how many are at the same Y level as the break.
     * Falling-block XP is awarded when sameLevelCount <= 1 (we broke the only block at that level).
     * Event runs before removal so the broken block is still present during BFS => sameLevelCount == 1 when alone.
     */
    private static final class ConnectedSupportResult {
        final int totalCount;
        final int sameLevelCount;

        ConnectedSupportResult(final int totalCount, final int sameLevelCount) {
            this.totalCount = totalCount;
            this.sameLevelCount = sameLevelCount;
        }
    }

    /**
     * Counts connected blocks of the same type that have support physics (e.g. tree logs that will fall).
     * BFS from the 6 neighbours of the broken block. The event runs before removal so the broken block is still
     * in the world and is included in the count when we traverse to it from a neighbour.
     * sameLevelCount = number of connected support blocks at the break's Y level (including the one we're breaking).
     */
    private ConnectedSupportResult countConnectedSupportBlocks(
            @Nonnull final World world,
            final int originX,
            final int originY,
            final int originZ,
            @Nonnull final BlockType sameType,
            final int maxBlocks
    ) {
        final int[] dx = { 1, -1, 0, 0, 0, 0 };
        final int[] dy = { 0, 0, 1, -1, 0, 0 };
        final int[] dz = { 0, 0, 0, 0, 1, -1 };
        final Set<String> visited = new HashSet<>();
        final Queue<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < 6; i++) {
            queue.add(new int[] { originX + dx[i], originY + dy[i], originZ + dz[i] });
        }
        int totalCount = 0;
        int sameLevelCount = 0;
        while (!queue.isEmpty() && totalCount < maxBlocks) {
            final int[] pos = queue.poll();
            final int x = pos[0], y = pos[1], z = pos[2];
            final String key = x + "," + y + "," + z;
            if (!visited.add(key)) {
                continue;
            }
            final BlockType at = world.getBlockType(x, y, z);
            if (at == null || !at.getId().equals(sameType.getId()) || at.getSupportDropType() == null) {
                continue;
            }
            totalCount++;
            if (y == originY) {
                sameLevelCount++;
            }
            for (int i = 0; i < 6; i++) {
                queue.add(new int[] { x + dx[i], y + dy[i], z + dz[i] });
            }
        }
        return new ConnectedSupportResult(totalCount, sameLevelCount);
    }

    private void spawnBonusDrops(
            @Nonnull final BreakBlockEvent event,
            @Nonnull final BlockType blockType,
            final double bonusPercent,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer
    ) {
        String itemId = null;
        String dropListId = null;
        int baseQuantity = 1;
        final BlockGathering gathering = blockType.getGathering();
        if (gathering != null) {
            if (gathering.getBreaking() != null) {
                BlockBreakingDropType breaking = gathering.getBreaking();
                itemId = breaking.getItemId();
                dropListId = breaking.getDropListId();
                baseQuantity = breaking.getQuantity();
            } else if (gathering.getSoft() != null) {
                SoftBlockDropType soft = gathering.getSoft();
                itemId = soft.getItemId();
                dropListId = soft.getDropListId();
            } else if (gathering.getHarvest() != null) {
                HarvestingDropType harvest = gathering.getHarvest();
                itemId = harvest.getItemId();
                dropListId = harvest.getDropListId();
            }
        }

        if (itemId == null && dropListId == null) {
            return;
        }

        // n% chance to drop one extra block (not "always n% more blocks")
        if (random.nextDouble() >= bonusPercent) {
            return;
        }

        List<ItemStack> bonusStacks = BlockHarvestUtils.getDrops(blockType, baseQuantity, itemId, dropListId);
        if (bonusStacks == null || bonusStacks.isEmpty()) {
            return;
        }

        Vector3d dropPos = new Vector3d(
                event.getTargetBlock().getX() + 0.5,
                event.getTargetBlock().getY(),
                event.getTargetBlock().getZ() + 0.5
        );
        var holders = ItemComponent.generateItemDrops(store, bonusStacks, dropPos, Vector3f.ZERO);
        for (var holder : holders) {
            commandBuffer.addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
