package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies mod-specific stat modifiers for players based on equipped items (e.g. EOO_Ring_Stamina).
 * If the stamina ring is in any player inventory (including bauble slots), max stamina is 25; otherwise default 10.
 */
public final class PlayerStatModifierService {

    private static final String STAMINA_MODIFIER_KEY = "EooStaminaBase";
    /** Item ID for the stamina ring (Server/Item/Items/EOO_Ring_Stamina.json). Game may use path "Items/EOO_Ring_Stamina". */
    public static final String RING_STAMINA_ITEM_ID = "EOO_Ring_Stamina";
    public static final String RING_STAMINA_ITEM_ID_PATH = "Items/EOO_Ring_Stamina";

    /** Max stamina when ring is present (vanilla base is 10). */
    private static final float TARGET_STAMINA_MAX = 25f;

    private PlayerStatModifierService() {}

    /**
     * Returns true if the given inventory or bauble container contains at least one EOO_Ring_Stamina.
     */
    public static boolean hasRingInInventory(
            @Nonnull Inventory inventory,
            @Nullable ItemContainer baubleContainer
    ) {
        if (containsRing(inventory.getHotbar())) return true;
        if (containsRing(inventory.getStorage())) return true;
        if (containsRing(inventory.getArmor())) return true;
        if (containsRing(inventory.getUtility())) return true;
        if (containsRing(inventory.getTools())) return true;
        if (containsRing(inventory.getBackpack())) return true;
        if (baubleContainer != null && containsRing(baubleContainer)) return true;
        return false;
    }

    private static boolean containsRing(@Nonnull ItemContainer container) {
        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !ItemStack.isEmpty(stack)) {
                String id = stack.getItemId();
                if (RING_STAMINA_ITEM_ID.equals(id) || RING_STAMINA_ITEM_ID_PATH.equals(id)) return true;
            }
        }
        return false;
    }

    /**
     * Applies or removes the stamina modifier so max stamina is 25 when hasRing is true, else default 10.
     * Caller should then set StatModifiersManager.setRecalculate(true) on the entity.
     */
    public static void updateStaminaFromRing(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            boolean hasRing
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;

        int staminaIndex = DefaultEntityStatTypes.getStamina();
        if (staminaIndex == Integer.MIN_VALUE) return;

        if (hasRing) {
            EntityStatType staminaAsset = EntityStatType.getAssetMap().getAsset(staminaIndex);
            if (staminaAsset == null) return;
            float additiveAmount = TARGET_STAMINA_MAX - staminaAsset.getMax();
            StaticModifier staminaModifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    additiveAmount
            );
            statMap.putModifier(staminaIndex, STAMINA_MODIFIER_KEY, staminaModifier);
        } else {
            statMap.removeModifier(staminaIndex, STAMINA_MODIFIER_KEY);
        }
    }
}
