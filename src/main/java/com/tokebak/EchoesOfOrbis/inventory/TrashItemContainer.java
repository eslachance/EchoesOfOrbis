package com.tokebak.EchoesOfOrbis.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

import javax.annotation.Nullable;

/**
 * Single-slot container that "destroys" items: anything placed here is discarded
 * (not stored). Used with Page.Bench via setPageWithWindows to show a trash slot.
 */
public class TrashItemContainer extends SimpleItemContainer {

    public TrashItemContainer() {
        super((short) 1);
    }

    @Nullable
    @Override
    protected ItemStack internal_setSlot(short slot, @Nullable ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return super.internal_removeSlot(slot);
        }
        // Destroy: discard the incoming item; clear slot and return previous content
        ItemStack previous = internal_removeSlot(slot);
        return previous;
    }

    @Override
    protected boolean cantAddToSlot(short slot, ItemStack itemStack, ItemStack slotItemStack) {
        return false;
    }

    @Override
    protected boolean cantRemoveFromSlot(short slot) {
        return false;
    }

    @Override
    protected boolean cantDropFromSlot(short slot) {
        return false;
    }

    @Override
    protected boolean cantMoveToSlot(ItemContainer fromContainer, short slotFrom) {
        return false;
    }
}
