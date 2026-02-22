package com.tokebak.EchoesOfOrbis.services.effects;

import java.util.EnumSet;
import java.util.Set;

/**
 * Categories of weapons that determine which effects can apply.
 * 
 * - PHYSICAL: Melee weapons (swords, axes, battleaxes, etc.)
 * - PROJECTILE: Ranged weapons that fire projectiles (bows, crossbows, guns)
 * - MAGIC: Magical weapons (wands, staves)
 */
public enum WeaponCategory {
    
    /**
     * Melee physical weapons: swords, axes, battleaxes, hammers, etc.
     * Identified by DamageCause.PHYSICAL
     */
    PHYSICAL("physical"),
    
    /**
     * Ranged projectile weapons: bows, crossbows, guns, etc.
     * Identified by DamageCause.PROJECTILE
     */
    PROJECTILE("projectile"),
    
    /**
     * Magical weapons: wands, staves, etc.
     * Identified by item ID containing "wand" or "staff"
     */
    MAGIC("magic"),

    /**
     * Bauble rings: items with tag Bauble_Ring. Use XP/upgrade system like weapons.
     */
    RING("ring"),

    /**
     * Armor: items in armor slots (Head, Chest, Hands, Legs). Defensive effects only.
     */
    ARMOR("armor");

    private final String id;
    
    WeaponCategory(final String id) {
        this.id = id;
    }
    
    public String getId() {
        return this.id;
    }
    
    /**
     * Parse category from string ID.
     */
    public static WeaponCategory fromId(final String id) {
        if (id == null) {
            return null;
        }
        for (final WeaponCategory category : values()) {
            if (category.id.equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }
    
    /**
     * Get a set containing all categories.
     */
    public static Set<WeaponCategory> all() {
        return EnumSet.allOf(WeaponCategory.class);
    }
    
    /**
     * Get a set containing only the specified categories.
     */
    public static Set<WeaponCategory> of(final WeaponCategory... categories) {
        if (categories == null || categories.length == 0) {
            return EnumSet.noneOf(WeaponCategory.class);
        }
        return EnumSet.of(categories[0], categories);
    }
    
    /**
     * Get a set for melee weapons (physical only).
     */
    public static Set<WeaponCategory> melee() {
        return EnumSet.of(PHYSICAL);
    }
    
    /**
     * Get a set for ranged weapons (projectile and magic).
     */
    public static Set<WeaponCategory> ranged() {
        return EnumSet.of(PROJECTILE, MAGIC);
    }

    /**
     * Get a set for bauble rings only.
     */
    public static Set<WeaponCategory> ring() {
        return EnumSet.of(RING);
    }

    /**
     * Get a set for armor only.
     */
    public static Set<WeaponCategory> armor() {
        return EnumSet.of(ARMOR);
    }

    /**
     * Get a set for both rings and armor (effects that can apply to either).
     */
    public static Set<WeaponCategory> ringAndArmor() {
        return EnumSet.of(RING, ARMOR);
    }

    /**
     * Get a set for weapons only (physical, projectile, magic). Excludes RING.
     */
    public static Set<WeaponCategory> weapons() {
        return EnumSet.of(PHYSICAL, PROJECTILE, MAGIC);
    }
}
