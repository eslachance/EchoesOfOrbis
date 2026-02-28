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
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import com.tokebak.EchoesOfOrbis.utils.WeaponSwapUtil;

import javax.annotation.Nonnull;

/**
 * Custom interaction that awards tool XP when a sickle successfully harvests a crop.
 * Inserted into the sickle selector's HitBlock chain as the {@code Next} of
 * {@code HarvestCrop}, so it only fires on a successful harvest (not on non-crop blocks).
 *
 * The sickle is classified as a Weapon (not Tool) in Hytale, so {@code getTool()} is null
 * and the event-based tool XP systems never fire. This interaction bridges that gap.
 */
public final class EOO_SickleCropXpInteraction extends SimpleInstantInteraction {

    public static final String ID = "EOO_SickleCropXp";

    private static final double TOOL_XP_PER_CROP_HARVEST = 2.0;

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
            } else if (hudDisplaySystem != null) {
                hudDisplaySystem.updateHudForPlayer(playerRef, currentTool, activeSlot);
            }
        }
        context.getState().state = InteractionState.Finished;
    }
}
