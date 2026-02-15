package com.tokebak.EchoesOfOrbis.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.inventory.TrashItemContainer;

import javax.annotation.Nonnull;

/**
 * Opens a trash bin (single slot) via Page.Bench + ContainerWindow.
 * Items placed in the slot are destroyed. Use this to verify that
 * setPageWithWindows(Page.Bench, ContainerWindow) shows inventory slots.
 */
public class TrashCommand extends AbstractPlayerCommand {

    private static final Message MESSAGE_TRASH_OPENED = Message.raw("Trash bin opened. Items placed here will be destroyed when replaced.");

    public TrashCommand() {
        super("trash", "Opens a trash bin to destroy items");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            TrashItemContainer trashContainer = new TrashItemContainer();
            Window[] windows = new Window[]{new ContainerWindow(trashContainer)};
            if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, windows)) {
                context.sendMessage(MESSAGE_TRASH_OPENED);
            }
        }
    }
}
