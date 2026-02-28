package com.tokebak.EchoesOfOrbis.utils;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Utilities for determining entity harvest drops (e.g. shears on sheep).
 * Uses the same drop list resolution as block harvest: {@link ItemModule#getRandomItemDrops(String)}.
 * <p>
 * The harvest drop list ID (e.g. {@value #DROP_LIST_SHEEP_HARVEST}) comes from the NPC role asset
 * (HarvestDropList). When using shears, the game's ContextualUseNPC interaction matches context
 * "Shear" to the entity's HarvestInteractionContext and uses its HarvestDropList. This util does
 * not resolve the drop list from the entity; callers must supply the drop list ID (e.g. from
 * config or a known constant for sheep).
 */
public final class EntityHarvestDropUtils {

    /** Drop list ID for sheep shear harvest (vanilla NPC role HarvestDropList). */
    public static final String DROP_LIST_SHEEP_HARVEST = "Drop_Sheep_Harvest";

    private EntityHarvestDropUtils() {
    }

    /**
     * Gets one roll of harvest drops for the given drop list ID.
     * This is the same as a single harvest use (e.g. one shear on a sheep).
     *
     * @param dropListId harvest drop list asset ID (e.g. {@link #DROP_LIST_SHEEP_HARVEST}), or null
     * @return list of item stacks that would drop from one harvest; empty if dropListId is null or ItemModule is disabled
     */
    @Nullable
    public static List<ItemStack> getHarvestDrops(@Nullable String dropListId) {
        if (dropListId == null) {
            return List.of();
        }
        ItemModule itemModule = ItemModule.get();
        if (itemModule == null || !itemModule.isEnabled()) {
            return List.of();
        }
        return itemModule.getRandomItemDrops(dropListId);
    }

    /**
     * Number of item stacks that would be dropped from one harvest use for the given drop list.
     * Each stack may have quantity &gt; 1.
     *
     * @param dropListId harvest drop list asset ID, or null
     * @return number of stacks (0 if null or module disabled)
     */
    public static int getHarvestDropStackCount(@Nullable String dropListId) {
        List<ItemStack> drops = getHarvestDrops(dropListId);
        return drops == null ? 0 : drops.size();
    }

    /**
     * Total number of items (sum of stack quantities) that would be dropped from one harvest use.
     *
     * @param dropListId harvest drop list asset ID, or null
     * @return total item count (0 if null or module disabled)
     */
    public static int getHarvestDropItemCount(@Nullable String dropListId) {
        List<ItemStack> drops = getHarvestDrops(dropListId);
        if (drops == null || drops.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : drops) {
            if (stack != null && !stack.isEmpty()) {
                total += stack.getQuantity();
            }
        }
        return total;
    }
}
