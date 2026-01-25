package com.tokebak.EchoesOfOrbis.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Main_Page;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

/**
 * Command /eoo - Opens the Echoes of Orbis item experience UI.
 * 
 * Shows all weapons and tools in the player's inventory with:
 * - Item icon and name
 * - Current level
 * - XP progress (current/next level)
 * - Applied effects and their values
 */
public class EooCommand extends AbstractPlayerCommand {
    
    @Nonnull
    private final ItemExpService itemExpService;
    
    public EooCommand(@Nonnull final ItemExpService itemExpService) {
        super("eoo", "Opens the Echoes of Orbis item experience interface");
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
        Player player = context.senderAs(Player.class);

        CompletableFuture.runAsync(() -> {
            player.getPageManager().openCustomPage(ref, store, new EOO_Main_Page(playerRef, CustomPageLifetime.CanDismiss, this.itemExpService));
            playerRef.sendMessage(Message.raw("UI Page Shown"));
        }, world);

    }
}
