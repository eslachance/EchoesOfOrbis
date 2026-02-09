package com.tokebak.EchoesOfOrbis.io;

import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.handlers.IPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SubPacketHandler;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * SubPacketHandler for packet 290 (SyncInteractionChains).
 * Forwards packets to the interaction queue. Previously attempted to intercept
 * Use (F-key) when UseBlock would fail to open the upgrade menu directly, but
 * that caused client disconnects (likely threading/ECS access from packet thread).
 * Kept as a pass-through handler so the mod can be extended later if needed.
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
        System.out.println("[EOO Packet] SyncInteractionChains received, updates=" + syncPacket.updates.length);
        forwardToQueue(syncPacket);
    }

    private void forwardToQueue(@Nonnull final SyncInteractionChains packet) {
        if (!(packetHandler instanceof GamePacketHandler)) {
            System.out.println("[EOO Packet] Cannot forward: packetHandler is not GamePacketHandler");
            return;
        }
        final GamePacketHandler gameHandler = (GamePacketHandler) packetHandler;
        Collections.addAll(gameHandler.getInteractionPacketQueue(), packet.updates);
        System.out.println("[EOO Packet] Forwarded " + packet.updates.length + " updates to interaction queue");
    }
}
