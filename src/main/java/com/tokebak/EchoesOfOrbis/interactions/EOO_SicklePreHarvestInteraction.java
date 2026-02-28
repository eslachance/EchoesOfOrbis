package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Runs immediately before {@code HarvestCrop} in the sickle's HitBlock chain.
 * Captures the target block's {@link BlockType} and position (which still has its
 * Harvest gathering at this point) and stores them in {@link SickleHarvestCache} so that
 * {@link EOO_SickleCropXpInteraction} can use them for bonus-drop rolls after the harvest.
 */
public final class EOO_SicklePreHarvestInteraction extends SimpleInstantInteraction {

    public static final String ID = "EOO_SicklePreHarvest";

    public static final BuilderCodec<EOO_SicklePreHarvestInteraction> CODEC =
            BuilderCodec.builder(EOO_SicklePreHarvestInteraction.class, EOO_SicklePreHarvestInteraction::new, SimpleInstantInteraction.CODEC).build();

    public EOO_SicklePreHarvestInteraction() {
    }

    @Override
    protected void firstRun(
            @Nonnull final InteractionType type,
            @Nonnull final InteractionContext context,
            @Nonnull final CooldownHandler cooldown
    ) {
        final BlockPosition targetBlock = context.getTargetBlock();
        System.out.println("[EOO] SicklePreHarvest: targetBlock=" + (targetBlock != null ? targetBlock.x + "," + targetBlock.y + "," + targetBlock.z : "NULL"));

        if (targetBlock != null) {
            try {
                final EntityStore entityStore = (EntityStore) context.getCommandBuffer().getStore().getExternalData();
                final World world = entityStore.getWorld();
                if (world != null) {
                    final BlockType blockType = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
                    System.out.println("[EOO] SicklePreHarvest: cached blockType=" + (blockType != null ? blockType.getId() : "NULL") + " at " + targetBlock.x + "," + targetBlock.y + "," + targetBlock.z);
                    SickleHarvestCache.store(blockType, targetBlock);
                } else {
                    System.out.println("[EOO] SicklePreHarvest: world is null");
                }
            } catch (final Exception e) {
                System.out.println("[EOO] SicklePreHarvest: failed to cache block info: " + e.getMessage());
            }
        }
        context.getState().state = InteractionState.Finished;
    }
}
