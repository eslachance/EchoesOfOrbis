package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.window.OpenWindow;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.BsonUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bson.BsonDocument;

/**
 * Manages a per-player "bauble" container (3 slots) that acts like a small backpack.
 * Opens as its own inventory window so items can be moved to/from main inventory.
 * Persists container contents to plugin data directory (bauble/{uuid}.json) on disconnect;
 * loads on first getOrCreate for that player.
 */
public final class BaubleContainerService {

    private static final short BAUBLE_SLOTS = 3;
    private static final String BAUBLE_SUBDIR = "bauble";
    private static final String FILE_EXT = ".json";

    private final Map<UUID, ItemContainer> containersByPlayer = new ConcurrentHashMap<>();
    /** Reverse map so we can notify which player's bauble container changed. */
    private final Map<ItemContainer, UUID> containerToPlayer = new ConcurrentHashMap<>();

    private volatile Consumer<UUID> onBaubleContainerChange;
    private Path storageDir;
    private HytaleLogger logger;

    /**
     * Set the directory for persisting bauble data (e.g. plugin getDataDirectory()).
     * Data is stored under {storageDir}/bauble/{uuid}.json.
     */
    public void setStorageDir(@Nullable Path storageDir) {
        this.storageDir = storageDir;
    }

    /**
     * Set logger for persistence errors (e.g. plugin getLogger()).
     */
    public void setLogger(@Nullable HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Set callback to run when any bauble container's contents change (e.g. to refresh stamina from ring).
     */
    public void setOnBaubleContainerChange(@Nullable Consumer<UUID> callback) {
        this.onBaubleContainerChange = callback;
    }

    /**
     * Returns the 3-slot bauble container for this player, creating it if needed.
     * Loads from disk if a saved file exists; otherwise creates an empty container.
     * New containers get a change listener that notifies the owner's UUID when contents change.
     */
    @Nonnull
    public ItemContainer getOrCreate(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        return containersByPlayer.computeIfAbsent(uuid, u -> {
            SimpleItemContainer loaded = loadForPlayer(u);
            final ItemContainer container;
            if (loaded != null && loaded.getCapacity() == BAUBLE_SLOTS) {
                container = loaded;
            } else if (loaded != null) {
                container = new SimpleItemContainer(BAUBLE_SLOTS);
                for (short i = 0; i < loaded.getCapacity() && i < BAUBLE_SLOTS; i++) {
                    ItemStack stack = loaded.getItemStack(i);
                    if (stack != null && !ItemStack.isEmpty(stack)) {
                        container.replaceItemStackInSlot(i, null, stack);
                    }
                }
            } else {
                container = new SimpleItemContainer(BAUBLE_SLOTS);
            }
            containerToPlayer.put(container, u);
            container.registerChangeEvent(e -> {
                Consumer<UUID> callback = onBaubleContainerChange;
                if (callback != null) callback.accept(u);
            });
            return container;
        });
    }

    /**
     * Saves this player's bauble container to disk. Call before cleanup on disconnect.
     */
    public void savePlayer(@Nonnull UUID playerUuid) {
        if (storageDir == null) return;
        ItemContainer container = containersByPlayer.get(playerUuid);
        if (container == null || !(container instanceof SimpleItemContainer)) return;
        Path file = storageDir.resolve(BAUBLE_SUBDIR).resolve(playerUuid.toString() + FILE_EXT);
        try {
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            BsonUtil.writeSync(file, SimpleItemContainer.CODEC, (SimpleItemContainer) container,
                    logger != null ? logger : HytaleLogger.forEnclosingClass());
        } catch (IOException e) {
            if (logger != null) {
                logger.atWarning().withCause(e).log("Failed to save bauble data for %s", playerUuid);
            }
        }
    }

    @Nullable
    private SimpleItemContainer loadForPlayer(@Nonnull UUID playerUuid) {
        if (storageDir == null) return null;
        Path file = storageDir.resolve(BAUBLE_SUBDIR).resolve(playerUuid.toString() + FILE_EXT);
        if (!Files.isRegularFile(file)) return null;
        BsonDocument doc = BsonUtil.readDocumentNow(file);
        if (doc == null) return null;
        try {
            return SimpleItemContainer.CODEC.decode(doc, EmptyExtraInfo.EMPTY);
        } catch (Exception e) {
            if (logger != null) {
                logger.atWarning().withCause(e).log("Failed to load bauble data for %s", playerUuid);
            }
            return null;
        }
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
