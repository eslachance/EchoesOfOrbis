package com.tokebak.EchoesOfOrbis.interactions;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import javax.annotation.Nullable;

/**
 * Thread-local cache that passes harvest block info from {@link EOO_SicklePreHarvestInteraction}
 * (which runs BEFORE {@code HarvestCrop}) to {@link EOO_SickleCropXpInteraction}
 * (which runs AFTER {@code HarvestCrop}). This is needed because {@code HarvestCrop}
 * modifies the block in-place, making the original harvest drop info unavailable afterward.
 *
 * Also stores the {@link BlockPosition} so bonus drops can be spawned at the correct
 * location even if the post-harvest context no longer provides it.
 */
public final class SickleHarvestCache {

    private static final ThreadLocal<CachedHarvestInfo> CACHE = new ThreadLocal<>();

    private SickleHarvestCache() {
    }

    public static void store(@Nullable final BlockType blockType, @Nullable final BlockPosition position) {
        if (blockType == null) {
            return;
        }
        CACHE.set(new CachedHarvestInfo(blockType, position));
    }

    @Nullable
    public static CachedHarvestInfo consumeAndClear() {
        final CachedHarvestInfo info = CACHE.get();
        CACHE.remove();
        return info;
    }

    public static final class CachedHarvestInfo {
        private final BlockType blockType;
        @Nullable
        private final BlockPosition position;

        CachedHarvestInfo(final BlockType blockType, @Nullable final BlockPosition position) {
            this.blockType = blockType;
            this.position = position;
        }

        public BlockType getBlockType() {
            return blockType;
        }

        @Nullable
        public BlockPosition getPosition() {
            return position;
        }
    }
}
