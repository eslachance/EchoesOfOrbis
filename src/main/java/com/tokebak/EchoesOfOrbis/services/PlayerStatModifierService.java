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
 * Applies mod-specific stat modifiers for players based on equipped items (rings).
 * - EOO_Ring_Stamina: max stamina 25 when in inventory (else default 10).
 * - EOO_Ring_Health: max health +50 when in inventory.
 */
public final class PlayerStatModifierService {

    private static final String STAMINA_MODIFIER_KEY = "EooStaminaBase";
    private static final String HEALTH_MODIFIER_KEY = "EooHealthBase";

    /** Item ID for the stamina ring. Game may use path "Items/EOO_Ring_Stamina". */
    public static final String RING_STAMINA_ITEM_ID = "EOO_Ring_Stamina";
    public static final String RING_STAMINA_ITEM_ID_PATH = "Items/EOO_Ring_Stamina";

    /** Item ID for the health ring. Game may use path "Items/EOO_Ring_Health". */
    public static final String RING_HEALTH_ITEM_ID = "EOO_Ring_Health";
    public static final String RING_HEALTH_ITEM_ID_PATH = "Items/EOO_Ring_Health";

    /** Max stamina when stamina ring is present (vanilla base is 10). */
    private static final float TARGET_STAMINA_MAX = 25f;
    /** Additive max health bonus when health ring is present. */
    private static final float HEALTH_RING_BONUS = 50f;

    /** All ring item IDs (and path variants) allowed in bauble ring slots. */
    private static final String[] RING_ITEM_IDS = new String[]{
            RING_STAMINA_ITEM_ID, RING_STAMINA_ITEM_ID_PATH,
            RING_HEALTH_ITEM_ID, RING_HEALTH_ITEM_ID_PATH
    };

    private PlayerStatModifierService() {}

    /**
     * Returns true if the given item stack is one of our ring items (allowed in bauble ring slots).
     */
    public static boolean isRingItem(@Nullable ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack)) return false;
        String id = stack.getItemId();
        for (String ringId : RING_ITEM_IDS) {
            if (ringId.equals(id)) return true;
        }
        return false;
    }

    /**
     * Returns true if the given inventory or bauble container contains at least one EOO_Ring_Stamina.
     */
    public static boolean hasStaminaRingInInventory(
            @Nonnull Inventory inventory,
            @Nullable ItemContainer baubleContainer
    ) {
        return hasItemInInventory(inventory, baubleContainer, RING_STAMINA_ITEM_ID, RING_STAMINA_ITEM_ID_PATH);
    }

    /**
     * Returns true if the given inventory or bauble container contains at least one EOO_Ring_Health.
     */
    public static boolean hasHealthRingInInventory(
            @Nonnull Inventory inventory,
            @Nullable ItemContainer baubleContainer
    ) {
        return hasItemInInventory(inventory, baubleContainer, RING_HEALTH_ITEM_ID, RING_HEALTH_ITEM_ID_PATH);
    }

    private static boolean hasItemInInventory(
            @Nonnull Inventory inventory,
            @Nullable ItemContainer baubleContainer,
            @Nonnull String... itemIds
    ) {
        if (containsAny(inventory.getHotbar(), itemIds)) return true;
        if (containsAny(inventory.getStorage(), itemIds)) return true;
        if (containsAny(inventory.getArmor(), itemIds)) return true;
        if (containsAny(inventory.getUtility(), itemIds)) return true;
        if (containsAny(inventory.getTools(), itemIds)) return true;
        if (containsAny(inventory.getBackpack(), itemIds)) return true;
        if (baubleContainer != null && containsAny(baubleContainer, itemIds)) return true;
        return false;
    }

    private static boolean containsAny(@Nonnull ItemContainer container, @Nonnull String... itemIds) {
        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !ItemStack.isEmpty(stack)) {
                String id = stack.getItemId();
                for (String itemId : itemIds) {
                    if (itemId.equals(id)) return true;
                }
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

    /**
     * Applies or removes the health modifier: +50 max health when hasRing is true.
     * Caller should then set StatModifiersManager.setRecalculate(true) on the entity.
     */
    public static void updateHealthFromRing(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            boolean hasRing
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (healthIndex == Integer.MIN_VALUE) return;

        if (hasRing) {
            StaticModifier healthModifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    HEALTH_RING_BONUS
            );
            statMap.putModifier(healthIndex, HEALTH_MODIFIER_KEY, healthModifier);
        } else {
            statMap.removeModifier(healthIndex, HEALTH_MODIFIER_KEY);
        }
    }
}
