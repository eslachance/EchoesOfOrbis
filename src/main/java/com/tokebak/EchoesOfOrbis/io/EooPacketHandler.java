package com.tokebak.EchoesOfOrbis.io;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Embue_Selection_Page;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Intercepts SyncInteractionChains (packet 290) to handle F-key (Use) when there is no target block.
 * When UseBlock fails (no target), the server normally waits for client sync that may never arrive,
 * so our chain's Failed branch never runs. This handler intercepts Use-with-no-target and opens
 * the upgrade menu directly, bypassing the stuck chain.
 */
public final class EooPacketHandler implements SubPacketHandler {

    private static final int PACKET_SYNC_INTERACTION_CHAINS = 290;

    @Nonnull
    private final IPacketHandler packetHandler;

    public EooPacketHandler(@Nonnull final IPacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    @Override
    public void registerHandlers() {
        packetHandler.registerHandler(PACKET_SYNC_INTERACTION_CHAINS, this::handleSyncInteractionChains);
    }

    private void handleSyncInteractionChains(@Nonnull final Object packet) {
        final SyncInteractionChains syncPacket = (SyncInteractionChains) packet;
        if (syncPacket.updates == null || syncPacket.updates.length == 0) {
            return;
        }

        final PlayerRef playerRef = packetHandler.getPlayerRef();
        final Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            forwardToQueue(syncPacket);
            return;
        }

        final List<SyncInteractionChain> toQueue = new ArrayList<>();
        for (final SyncInteractionChain update : syncPacket.updates) {
            if (isUseWithNoBlockTarget(update)) {
                scheduleOpenUpgradeMenu(playerRef, ref);
            } else {
                toQueue.add(update);
            }
        }

        if (toQueue.isEmpty()) {
            return;
        }

        forwardToQueue(toQueue);
    }

    private boolean isUseWithNoBlockTarget(@Nonnull final SyncInteractionChain update) {
        if (update.interactionType != InteractionType.Use) {
            return false;
        }
        if (!update.initial) {
            return false;
        }
        if (update.data == null) {
            return true;
        }
        return update.data.blockPosition == null;
    }

    private void scheduleOpenUpgradeMenu(@Nonnull final PlayerRef playerRef, @Nonnull final Ref<EntityStore> ref) {
        final Store<EntityStore> store = ref.getStore();
        final EntityStore entityStore = (EntityStore) store.getExternalData();
        final World world = entityStore.getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            openUpgradeMenuIfEligible(ref, store, playerRef);
        });
    }

    private void openUpgradeMenuIfEligible(
            @Nonnull final Ref<EntityStore> ref,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final PlayerRef playerRef
    ) {
        final Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        final Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        final byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot < 0) {
            return;
        }
        final ItemStack heldItem = inventory.getHotbar().getItemStack((short) activeSlot);
        if (heldItem == null || heldItem.isEmpty()) {
            return;
        }
        final ItemExpService itemExpService = ItemExpService.getInstance();
        if (itemExpService == null) {
            return;
        }
        if (!itemExpService.canGainXp(heldItem)) {
            return;
        }
        if (itemExpService.getPendingEmbues(heldItem) <= 0) {
            return;
        }

        final EOO_Embue_Selection_Page selectionPage = new EOO_Embue_Selection_Page(
                playerRef,
                com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime.CanDismiss,
                itemExpService,
                "Hotbar",
                activeSlot
        );
        player.getPageManager().openCustomPage(ref, store, selectionPage);
    }

    private void forwardToQueue(@Nonnull final SyncInteractionChains packet) {
        forwardToQueue(Arrays.asList(packet.updates));
    }

    private void forwardToQueue(@Nonnull final List<SyncInteractionChain> updates) {
        if (!(packetHandler instanceof GamePacketHandler)) {
            return;
        }
        final GamePacketHandler gameHandler = (GamePacketHandler) packetHandler;
        Collections.addAll(gameHandler.getInteractionPacketQueue(), updates.toArray(new SyncInteractionChain[0]));
    }
}
