package com.tokebak.EchoesOfOrbis.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Slot filter that uses a predicate on the item stack (e.g. "ring only").
 * Same pattern as ArcaneRig: only applies to ADD; REMOVE/DROP are allowed.
 */
public final class BaubleSlotFilter implements SlotFilter {

    private final Predicate<ItemStack> canAdd;

    public BaubleSlotFilter(Predicate<ItemStack> canAdd) {
        this.canAdd = canAdd;
    }

    @Override
    public boolean test(FilterActionType actionType, ItemContainer container, short slot, @Nullable ItemStack stack) {
        if (actionType != FilterActionType.ADD) return true;
        if (stack == null) return true;
        return canAdd.test(stack);
    }
}
