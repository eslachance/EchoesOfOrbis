package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages a per-player "bauble" container (3 slots) that acts like a small backpack.
 * Opens as its own inventory window so items can be moved to/from main inventory.
 * Data is in-memory only for now; persistence can be added later.
 */
public final class BaubleContainerService {

    private static final short BAUBLE_SLOTS = 3;

    private final Map<UUID, ItemContainer> containersByPlayer = new ConcurrentHashMap<>();
    /** Reverse map so we can notify which player's bauble container changed. */
    private final Map<ItemContainer, UUID> containerToPlayer = new ConcurrentHashMap<>();

    private volatile Consumer<UUID> onBaubleContainerChange;

    /**
     * Set callback to run when any bauble container's contents change (e.g. to refresh stamina from ring).
     */
    public void setOnBaubleContainerChange(@Nullable Consumer<UUID> callback) {
        this.onBaubleContainerChange = callback;
    }

    /**
     * Returns the 3-slot bauble container for this player, creating it if needed.
     * New containers get a change listener that notifies the owner's UUID when contents change.
     */
    @Nonnull
    public ItemContainer getOrCreate(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        return containersByPlayer.computeIfAbsent(uuid, u -> {
            ItemContainer container = new SimpleItemContainer(BAUBLE_SLOTS);
            containerToPlayer.put(container, u);
            container.registerChangeEvent(e -> {
                Consumer<UUID> callback = onBaubleContainerChange;
                if (callback != null) callback.accept(u);
            });
            return container;
        });
    }

    /**
     * Opens the bauble window for the player and sends the OpenWindow packet.
     * Use when you have ref, store, and Player (e.g. from EOO_Main_Page handleDataEvent).
     *
     * @return the window ID if the window was opened and the packet was sent, or -1 on failure
     */
    public int openBaubleWindow(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Player player
    ) {
        ItemContainer container = getOrCreate(player.getPlayerRef());
        ContainerWindow window = new ContainerWindow(container);
        OpenWindow packet = player.getWindowManager().openWindow(ref, window, store);
        if (packet != null) {
            player.getPlayerRef().getPacketHandler().writeNoCache((ToClientPacket) packet);
            return window.getId();
        }
        return -1;
    }

    /**
     * Opens the Bauble screen using the built-in Bench page with our 3-slot container.
     * This shows the proper inventory slot UI (same pattern as /trash and backpack).
     *
     * @return true if the page and window were set successfully
     */
    public boolean openBaubleAsBenchPage(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull Player player
    ) {
        ItemContainer container = getOrCreate(player.getPlayerRef());
        Window[] windows = new Window[]{new ContainerWindow(container)};
        return player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, windows);
    }

    /**
     * Removes the bauble container for this player (e.g. on disconnect).
     * Items in the container are not persisted in the current implementation.
     */
    public void cleanupPlayer(@Nonnull UUID playerUuid) {
        ItemContainer removed = containersByPlayer.remove(playerUuid);
        if (removed != null) containerToPlayer.remove(removed);
    }
}
