package com.tokebak.EchoesOfOrbis.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Embue_Selection_Page;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand /eoo upgrade - Opens the upgrade selection UI for the held weapon.
 * Use when F key doesn't work (e.g. when looking at sky with no target).
 */
public class EooUpgradeCommand extends AbstractPlayerCommand {

    @Nonnull
    private final ItemExpService itemExpService;

    public EooUpgradeCommand(@Nonnull final ItemExpService itemExpService) {
        super("upgrade", "Opens the upgrade selection for the held weapon");
        this.itemExpService = itemExpService;
    }

    @Override
    protected void execute(
            @Nonnull final CommandContext context,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Ref<EntityStore> ref,
            @Nonnull final PlayerRef playerRef,
            @Nonnull final World world
    ) {
        final Player player = context.senderAs(Player.class);
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            playerRef.sendMessage(Message.raw("[EOO] No inventory"));
            return;
        }

        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot < 0) {
            playerRef.sendMessage(Message.raw("[EOO] No item selected"));
            return;
        }

        final ItemStack heldItem = inventory.getHotbar().getItemStack((short) activeSlot);
        if (heldItem == null || heldItem.isEmpty()) {
            playerRef.sendMessage(Message.raw("[EOO] Hold a weapon to upgrade"));
            return;
        }

        if (!itemExpService.canGainXp(heldItem)) {
            playerRef.sendMessage(Message.raw("[EOO] This item cannot gain XP"));
            return;
        }

        if (itemExpService.getPendingEmbues(heldItem) <= 0) {
            playerRef.sendMessage(Message.raw("[EOO] No pending upgrades. Level up your weapon first!"));
            return;
        }

        CompletableFuture.runAsync(() -> {
            final EOO_Embue_Selection_Page selectionPage = new EOO_Embue_Selection_Page(
                    playerRef,
                    CustomPageLifetime.CanDismiss,
                    itemExpService,
                    "Hotbar",
                    activeSlot
            );
            player.getPageManager().openCustomPage(ref, store, selectionPage);
        }, world);
    }
}
