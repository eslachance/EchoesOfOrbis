package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.EntityHarvestDropUtils;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;

import javax.annotation.Nonnull;

/**
 * Custom interaction that awards tool XP when the player uses a tool on an entity (e.g. shears on sheep).
 * Inserted as the first step in the shears Primary/Secondary chain via asset override so it runs
 * before ContextualUseNPC; then the chain continues and the actual shear happens.
 * <p>
 * XP is scaled by the number of vanilla drops (one roll of the entity's harvest drop list, before any
 * bonus effects): more drops = more XP, so shearing stays rewarding without a flat 2 XP per shear.
 */
public final class EOO_ToolEntityXpInteraction extends SimpleInstantInteraction {

    public static final String ID = "EOO_ToolEntityXp";

    /** XP awarded per vanilla drop item (before any bonus). Total XP = this * max(1, vanilla drop count). */
    private static final double TOOL_XP_PER_ENTITY_DROP = 2.0;

    public static final BuilderCodec<EOO_ToolEntityXpInteraction> CODEC =
            BuilderCodec.builder(EOO_ToolEntityXpInteraction.class, EOO_ToolEntityXpInteraction::new, SimpleInstantInteraction.CODEC).build();

    public EOO_ToolEntityXpInteraction() {
    }

    @Override
    protected void firstRun(
            @Nonnull final InteractionType type,
            @Nonnull final InteractionContext context,
            @Nonnull final CooldownHandler cooldown
    ) {
        if (context.getTargetEntity() == null) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        final ItemStack tool = context.getHeldItem();
        if (tool == null || tool.isEmpty() || tool.getItem() == null || tool.getItem().getTool() == null) {
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
        // Scale XP by vanilla drop count (sheep harvest list; no per-entity resolution here)
        final int vanillaDropCount = EntityHarvestDropUtils.getHarvestDropItemCount(EntityHarvestDropUtils.DROP_LIST_SHEEP_HARVEST);
        final double xpToAdd = TOOL_XP_PER_ENTITY_DROP * Math.max(1, vanillaDropCount);
        itemExpService.addPendingXp(playerRef, activeSlot, xpToAdd);
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
        context.getState().state = InteractionState.Finished;
    }
}
