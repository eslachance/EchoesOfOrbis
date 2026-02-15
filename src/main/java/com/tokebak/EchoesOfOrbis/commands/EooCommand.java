package com.tokebak.EchoesOfOrbis.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Main_Page;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Command /eoo - Opens the Echoes of Orbis item experience UI.
 */
public class EooCommand extends AbstractPlayerCommand {

    @Nonnull
    private final ItemExpService itemExpService;
    @Nonnull
    private final BaubleContainerService baubleContainerService;

    public EooCommand(@Nonnull final ItemExpService itemExpService, @Nonnull final BaubleContainerService baubleContainerService) {
        super("eoo", "Opens the Echoes of Orbis item experience interface");
        this.itemExpService = itemExpService;
        this.baubleContainerService = baubleContainerService;
    }

    @Override
    protected void execute(
            @Nonnull final CommandContext context,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Ref<EntityStore> ref,
            @Nonnull final PlayerRef playerRef,
            @Nonnull final World world
    ) {
        Player player = context.senderAs(Player.class);

        CompletableFuture.runAsync(() -> {
            player.getPageManager().openCustomPage(ref, store, new EOO_Main_Page(playerRef, CustomPageLifetime.CanDismiss, this.itemExpService, this.baubleContainerService));
        }, world);

    }
}
