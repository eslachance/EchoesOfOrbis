package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

/**
 * Awards tool XP and rolls for bonus drops when a sickle successfully harvests a crop.
 * Runs as the {@code Next} of {@code HarvestCrop} so it only fires on a successful harvest.
 *
 * Bonus drops rely on {@link SickleHarvestCache}, which is populated by
 * {@link EOO_SicklePreHarvestInteraction} BEFORE {@code HarvestCrop} modifies the block.
 */
public final class EOO_SickleCropXpInteraction extends SimpleInstantInteraction {

    public static final String ID = "EOO_SickleCropXp";

    private static final double TOOL_XP_PER_CROP_HARVEST = 2.0;
    private static final double DROP_BONUS_CAP = 0.50;
    private static final Random RANDOM = new Random();

    public static final BuilderCodec<EOO_SickleCropXpInteraction> CODEC =
            BuilderCodec.builder(EOO_SickleCropXpInteraction.class, EOO_SickleCropXpInteraction::new, SimpleInstantInteraction.CODEC).build();

    public EOO_SickleCropXpInteraction() {
    }

    @Override
    protected void firstRun(
            @Nonnull final InteractionType type,
            @Nonnull final InteractionContext context,
            @Nonnull final CooldownHandler cooldown
    ) {
        final SickleHarvestCache.CachedHarvestInfo harvestInfo = SickleHarvestCache.consumeAndClear();

        final ItemStack tool = context.getHeldItem();
        if (tool == null || tool.isEmpty() || tool.getItem() == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final ItemExpService itemExpService = ItemExpService.getInstance();
        if (itemExpService == null || !itemExpService.canGainXp(tool)) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final Ref<EntityStore> entityRef = context.getEntity();
        if (entityRef == null || !entityRef.isValid()) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final Store<EntityStore> store = (Store<EntityStore>) context.getCommandBuffer().getStore();
        final PlayerRef playerRef = (PlayerRef) store.getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final Player player = (Player) store.getComponent(entityRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final byte activeSlot = context.getHeldItemSlot();
        if (activeSlot < 0 || activeSlot > 8) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        final HudDisplaySystem hudDisplaySystem = HudDisplaySystem.getInstance();
        final int currentToolLevel = itemExpService.getItemLevel(tool);

        // ---- Tool XP ----
        itemExpService.addPendingXp(playerRef, activeSlot, TOOL_XP_PER_CROP_HARVEST);
        System.out.println("[EOO] SickleCropXp: awarded " + TOOL_XP_PER_CROP_HARVEST + " XP for crop harvest (slot=" + activeSlot + ")");

        ItemStack currentTool = inventory.getActiveHotbarItem();
        if (currentTool != null && !currentTool.isEmpty() && currentTool.getItem() != null) {
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
                currentTool = flushedTool;
            } else if (hudDisplaySystem != null) {
                hudDisplaySystem.updateHudForPlayer(playerRef, currentTool, activeSlot);
            }
        }

        // ---- Bonus drops (TOOL_DROP_BONUS) ----
        System.out.println("[EOO] SickleCropXp: harvestInfo=" + (harvestInfo != null ? harvestInfo.getBlockType().getId() : "NULL")
                + ", cachedPos=" + (harvestInfo != null && harvestInfo.getPosition() != null
                    ? harvestInfo.getPosition().x + "," + harvestInfo.getPosition().y + "," + harvestInfo.getPosition().z : "NULL")
                + ", contextTargetBlock=" + (context.getTargetBlock() != null
                    ? context.getTargetBlock().x + "," + context.getTargetBlock().y + "," + context.getTargetBlock().z : "NULL"));

        if (harvestInfo != null && currentTool != null && !currentTool.isEmpty()) {
            final int dropToolLevel = itemExpService.getItemLevel(currentTool);
            System.out.println("[EOO] SickleCropXp: dropToolLevel=" + dropToolLevel);
            if (dropToolLevel > 1) {
                final WeaponEffectsService effectsService = itemExpService.getEffectsService();
                double bonusPercent = 0;
                for (var inst : effectsService.getEffects(currentTool)) {
                    System.out.println("[EOO] SickleCropXp: effect=" + inst.getType() + " level=" + inst.getLevel());
                    if (inst.getType() == WeaponEffectType.TOOL_DROP_BONUS) {
                        var def = effectsService.getDefinition(inst.getType());
                        if (def != null) {
                            bonusPercent = Math.min(DROP_BONUS_CAP, def.calculateValue(inst.getLevel()));
                            break;
                        }
                    }
                }
                System.out.println("[EOO] SickleCropXp: bonusPercent=" + bonusPercent);
                if (bonusPercent > 0 && RANDOM.nextDouble() < bonusPercent) {
                    spawnBonusDrops(harvestInfo, context, store);
                }
            }
        }

        context.getState().state = InteractionState.Finished;
    }

    private static void spawnBonusDrops(
            @Nonnull final SickleHarvestCache.CachedHarvestInfo harvestInfo,
            @Nonnull final InteractionContext context,
            @Nonnull final Store<EntityStore> store
    ) {
        final BlockType originalBlockType = harvestInfo.getBlockType();
        final BlockGathering gathering = originalBlockType.getGathering();
        if (gathering == null || gathering.getHarvest() == null) {
            System.out.println("[EOO] SickleCropXp: no gathering/harvest on " + originalBlockType.getId());
            return;
        }
        final HarvestingDropType harvest = gathering.getHarvest();
        final List<ItemStack> bonusStacks = BlockHarvestUtils.getDrops(
                originalBlockType, 1, harvest.getItemId(), harvest.getDropListId());
        if (bonusStacks == null || bonusStacks.isEmpty()) {
            System.out.println("[EOO] SickleCropXp: getDrops returned empty for " + originalBlockType.getId());
            return;
        }

        BlockPosition blockPos = harvestInfo.getPosition();
        if (blockPos == null) {
            blockPos = context.getTargetBlock();
        }
        if (blockPos == null) {
            System.out.println("[EOO] SickleCropXp: no block position available for bonus drop spawn");
            return;
        }
        final Vector3d dropPos = new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5);
        var holders = ItemComponent.generateItemDrops(store, bonusStacks, dropPos, Vector3f.ZERO);
        for (var holder : holders) {
            context.getCommandBuffer().addEntity(holder, com.hypixel.hytale.component.AddReason.SPAWN);
        }
        System.out.println("[EOO] SickleCropXp: bonus drop spawned " + bonusStacks.size() + " stacks for " + originalBlockType.getId());
    }
}
