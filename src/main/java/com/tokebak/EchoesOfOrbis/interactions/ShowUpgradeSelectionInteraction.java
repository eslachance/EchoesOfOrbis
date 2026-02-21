package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.ui.EOO_Main_Page;

import javax.annotation.Nonnull;

/**
 * Custom interaction. When the player presses F (Use) and no world interaction applies
 * (no target entity, no harvestable block), opens the main Echoes of Orbis UI.
 * Does not require holding an upgradeable weapon.
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
        System.out.println("[EOO Interaction] EOO_ShowUpgradeSelection firstRun invoked, type=" + type 
            + ", targetBlock=" + context.getTargetBlock() 
            + ", targetEntity=" + context.getTargetEntity());

        // Fail if there's a target entity (UseEntity should have handled it, but just in case)
        if (context.getTargetEntity() != null) {
            System.out.println("[EOO Interaction] FAIL: targetEntity exists, UseEntity should have handled this");
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Fail if there's a harvestable target block (let BreakBlock handle it)
        final BlockPosition targetBlockPos = context.getTargetBlock();
        if (targetBlockPos != null) {
            final EntityStore entityStore = (EntityStore) context.getCommandBuffer().getStore().getExternalData();
            final World world = entityStore.getWorld();
            final ChunkStore chunkStore = world.getChunkStore();
            final Store chunkStoreStore = chunkStore.getStore();
            
            final long chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(targetBlockPos.x, targetBlockPos.z);
            final Ref chunkReference = chunkStore.getChunkReference(chunkIndex);
            
            if (chunkReference != null && chunkReference.isValid()) {
                final WorldChunk worldChunk = (WorldChunk) chunkStoreStore.getComponent(chunkReference, WorldChunk.getComponentType());
                if (worldChunk != null) {
                    final BlockType blockType = worldChunk.getBlockType(targetBlockPos.x, targetBlockPos.y, targetBlockPos.z);
                    if (BlockHarvestUtils.shouldPickupByInteraction(blockType)) {
                        System.out.println("[EOO Interaction] FAIL: targetBlock is harvestable (" + 
                            (blockType != null ? blockType.getId() : "null") + "), letting BreakBlock handle it");
                        context.getState().state = InteractionState.Failed;
                        return;
                    }
                }
            }
        }

        final ItemExpService itemExpService = ItemExpService.getInstance();
        if (itemExpService == null) {
            System.out.println("[EOO Interaction] FAIL: itemExpService=null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null || !playerRef.isValid()) {
            System.out.println("[EOO Interaction] FAIL: playerRef=null or invalid");
            context.getState().state = InteractionState.Failed;
            return;
        }
        final Store<EntityStore> store = playerRef.getStore();
        final PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            System.out.println("[EOO Interaction] FAIL: playerRefComponent=null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            System.out.println("[EOO Interaction] FAIL: player=null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final BaubleContainerService baubleContainerService = BaubleContainerService.getInstance();
        if (baubleContainerService == null) {
            System.out.println("[EOO Interaction] FAIL: baubleContainerService=null");
            context.getState().state = InteractionState.Failed;
            return;
        }

        final EOO_Main_Page mainPage = new EOO_Main_Page(
                playerRefComponent,
                CustomPageLifetime.CanDismiss,
                itemExpService,
                baubleContainerService
        );
        player.getPageManager().openCustomPage(playerRef, store, mainPage);

        System.out.println("[EOO Interaction] SUCCESS: opened main EOO UI");
        context.getState().state = InteractionState.Finished;
    }
}
