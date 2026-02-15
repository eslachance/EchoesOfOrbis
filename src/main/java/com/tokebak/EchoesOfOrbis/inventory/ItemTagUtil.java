package com.tokebak.EchoesOfOrbis.inventory;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Helpers for checking item tags (e.g. Bauble_Ring, Bauble_Neck).
 * Tags come from item JSON: "Tags": {"Type": ["Bauble_Ring"]} adds the tag "Bauble_Ring".
 */
public final class ItemTagUtil {

    private ItemTagUtil() {}

    /**
     * Returns true if the item stack's item has the given tag (e.g. "Bauble_Ring").
     * Empty or null stack returns false.
     */
    public static boolean hasTag(@Nullable ItemStack stack, @Nullable String tagName) {
        if (tagName == null || stack == null || ItemStack.isEmpty(stack)) return false;
        Item item = stack.getItem();
        if (item == null) return false;
        AssetExtraInfo.Data data = item.getData();
        if (data == null) return false;
        IntSet tagIndexes = data.getExpandedTagIndexes();
        if (tagIndexes == null) return false;
        int tagIndex = AssetRegistry.getOrCreateTagIndex(tagName);
        return tagIndexes.contains(tagIndex);
    }
}
