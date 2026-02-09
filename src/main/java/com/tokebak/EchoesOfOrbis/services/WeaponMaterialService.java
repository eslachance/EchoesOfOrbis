package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.server.core.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps weapon ItemIDs to material tiers and boost slot counts.
 * Material is suffix-based and weapon-type-agnostic: the same logic
 * applies to Swords, Axes, Maces, Daggers, Bows, etc.
 */
public final class WeaponMaterialService {

    private static final int DEFAULT_SLOTS = 2;

    /** Direct material tier mapping: suffix -> boost slots */
    private static final Map<String, Integer> MATERIAL_SLOTS = new HashMap<>();

    /** Enemy drop / variant fallback: suffix -> boost slots */
    private static final Map<String, Integer> VARIANT_SLOTS = new HashMap<>();

    static {
        // Primary crafting materials
        MATERIAL_SLOTS.put("Crude", 2);
        MATERIAL_SLOTS.put("Copper", 3);
        MATERIAL_SLOTS.put("Iron", 4);
        MATERIAL_SLOTS.put("Thorium", 5);
        MATERIAL_SLOTS.put("Cobalt", 6);
        MATERIAL_SLOTS.put("Adamantite", 7);
        MATERIAL_SLOTS.put("Mithril", 8);

        // Enemy drop / variant fallbacks
        VARIANT_SLOTS.put("Scrap", 2);
        VARIANT_SLOTS.put("Bone", 2);
        VARIANT_SLOTS.put("Stone_Trork", 2);
        VARIANT_SLOTS.put("Wood", 2);
        VARIANT_SLOTS.put("Tribal", 2);
        VARIANT_SLOTS.put("Fishbone", 2);
        VARIANT_SLOTS.put("Leaf", 2);
        VARIANT_SLOTS.put("Claw_Bone", 2);

        VARIANT_SLOTS.put("Bronze", 3);
        VARIANT_SLOTS.put("Bronze_Ancient", 3);
        VARIANT_SLOTS.put("Cutlass", 3);

        VARIANT_SLOTS.put("Steel", 4);
        VARIANT_SLOTS.put("Steel_Rusty", 4);
        VARIANT_SLOTS.put("Steel_Incandescent", 4);
        VARIANT_SLOTS.put("Iron_Rusty", 4);
        VARIANT_SLOTS.put("Ancient_Steel", 4);

        VARIANT_SLOTS.put("Doomed", 5);
        VARIANT_SLOTS.put("Frost", 5);
        VARIANT_SLOTS.put("Onyxium", 5);
        VARIANT_SLOTS.put("Runic", 5);
        VARIANT_SLOTS.put("Nexus", 5);
        VARIANT_SLOTS.put("Silversteel", 5);
        VARIANT_SLOTS.put("Scarab", 5);
        VARIANT_SLOTS.put("Claw_Tribal", 5);

        VARIANT_SLOTS.put("Gun", 2);
        VARIANT_SLOTS.put("Blunderbuss", 2);
        VARIANT_SLOTS.put("Blunderbuss_Rusty", 2);
    }

    private WeaponMaterialService() {
        // Utility class
    }

    /**
     * Get the number of boost slots for a weapon based on its material tier.
     *
     * @param weapon The weapon ItemStack
     * @return Number of boost slots (2-8), or 2 if unknown
     */
    public static int getBoostSlotsForWeapon(@Nullable final ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return DEFAULT_SLOTS;
        }
        return getBoostSlotsForItemId(weapon.getItemId());
    }

    /**
     * Get the number of boost slots for an item ID.
     *
     * @param itemId The item ID (e.g. "hytale:Weapon_Sword_Iron" or "Weapon_Sword_Iron")
     * @return Number of boost slots (2-8), or 2 if unknown
     */
    public static int getBoostSlotsForItemId(@Nullable final String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return DEFAULT_SLOTS;
        }
        final String suffix = extractMaterialSuffix(itemId);
        if (suffix == null || suffix.isEmpty()) {
            return DEFAULT_SLOTS;
        }
        // Check primary materials first
        final Integer primary = MATERIAL_SLOTS.get(suffix);
        if (primary != null) {
            return primary;
        }
        // Check variant fallbacks
        final Integer variant = VARIANT_SLOTS.get(suffix);
        if (variant != null) {
            return variant;
        }
        return DEFAULT_SLOTS;
    }

    /**
     * Extract the material suffix from a weapon ItemID.
     * E.g. "Weapon_Sword_Crude" -> "Crude", "Weapon_Axe_Iron_Rusty" -> "Iron_Rusty"
     */
    @Nullable
    public static String extractMaterialSuffix(@Nullable final String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        String cleaned = itemId;
        final int colonIndex = cleaned.indexOf(':');
        if (colonIndex != -1) {
            cleaned = cleaned.substring(colonIndex + 1);
        }
        if (!cleaned.startsWith("Weapon_") && !cleaned.startsWith("Tool_")) {
            return null;
        }
        final String withoutPrefix = cleaned.startsWith("Weapon_")
                ? cleaned.substring(7)
                : cleaned.substring(5);
        final int underscoreIndex = withoutPrefix.indexOf('_');
        if (underscoreIndex == -1) {
            return withoutPrefix.isEmpty() ? null : withoutPrefix;
        }
        return withoutPrefix.substring(underscoreIndex + 1);
    }
}
