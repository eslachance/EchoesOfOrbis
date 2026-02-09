package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Embue_Selection_Page;

import javax.annotation.Nonnull;

/**
 * Spellbook-style custom interaction. When the player presses F (Use) with a weapon
 * that has pending upgrades, opens the upgrade selection UI. Otherwise fails so the
 * chain continues to UseBlock/UseEntity/etc.
 *
 * Registered and wired into the Use interaction chain via Empty.json override.
 */
public final class ShowUpgradeSelectionInteraction extends SimpleInstantInteraction {

    public static final String ID = "EOO_ShowUpgradeSelection";

    public static final BuilderCodec<ShowUpgradeSelectionInteraction> CODEC =
            BuilderCodec.builder(ShowUpgradeSelectionInteraction.class, ShowUpgradeSelectionInteraction::new, SimpleInstantInteraction.CODEC).build();

    public ShowUpgradeSelectionInteraction() {
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldown
    ) {
        final ItemStack heldItem = context.getHeldItem();
        if (heldItem == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final ItemExpService itemExpService = ItemExpService.getInstance();
        if (itemExpService == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (!itemExpService.canGainXp(heldItem)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        if (itemExpService.getPendingEmbues(heldItem) <= 0) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        final Store<EntityStore> store = playerRef.getStore();
        final PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final byte activeSlot = context.getHeldItemSlot();
        if (activeSlot < 0) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        final EOO_Embue_Selection_Page selectionPage = new EOO_Embue_Selection_Page(
                playerRefComponent,
                CustomPageLifetime.CanDismiss,
                itemExpService,
                "Hotbar",
                activeSlot
        );
        player.getPageManager().openCustomPage(playerRef, store, selectionPage);

        context.getState().state = InteractionState.Finished;
    }
}
