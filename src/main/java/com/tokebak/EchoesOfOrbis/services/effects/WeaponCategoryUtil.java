package com.tokebak.EchoesOfOrbis.services.effects;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.tokebak.EchoesOfOrbis.inventory.ItemTagUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for determining weapon/ring categories.
 *
 * Categories are determined by:
 * 1. Bauble_Ring tag → RING
 * 2. The DamageCause from the damage event (PHYSICAL vs PROJECTILE)
 * 3. The item ID for magic weapon detection
 */
public final class WeaponCategoryUtil {
    
    // Item ID patterns for magic weapons
    private static final String[] MAGIC_PATTERNS = {
            "wand", "staff", "stave", "scepter", "orb", "tome"
    };
    
    // Item ID patterns for projectile weapons (in case DamageCause isn't enough)
    private static final String[] PROJECTILE_PATTERNS = {
            "bow", "crossbow", "gun", "rifle", "pistol", "launcher"
    };
    
    private WeaponCategoryUtil() {
        // Utility class
    }
    
    /**
     * Determine the weapon category from a damage event and the weapon used.
     * 
     * Priority:
     * 1. Check if weapon ID matches magic patterns → MAGIC
     * 2. Check DamageCause (PROJECTILE) → PROJECTILE
     * 3. Default to PHYSICAL for melee attacks
     * 
     * @param damage The damage event
     * @param weapon The weapon ItemStack (can be null)
     * @return The determined weapon category
     */
    @Nonnull
    public static WeaponCategory determineCategory(
            @Nullable final Damage damage,
            @Nullable final ItemStack weapon
    ) {
        // First check if item is a bauble ring (tag Bauble_Ring)
        if (weapon != null && !weapon.isEmpty() && ItemTagUtil.hasTag(weapon, "Bauble_Ring")) {
            return WeaponCategory.RING;
        }
        // Then check if weapon is a magic weapon by ID
        if (weapon != null && !weapon.isEmpty()) {
            final String itemId = weapon.getItemId();
            if (itemId != null && isMagicWeapon(itemId)) {
                return WeaponCategory.MAGIC;
            }
        }
        
        // Check DamageCause from the damage event
        if (damage != null) {
            final DamageCause cause = damage.getCause();
            if (cause != null) {
                final String causeId = cause.getId();
                if (causeId != null) {
                    final String lower = causeId.toLowerCase();
                    if (lower.contains("projectile")) return WeaponCategory.PROJECTILE;
                    if (lower.contains("magic") || lower.contains("spell")) return WeaponCategory.MAGIC;
                }
            }
        }
        
        // Check weapon ID for projectile weapons as fallback
        if (weapon != null && !weapon.isEmpty()) {
            final String itemId = weapon.getItemId();
            if (itemId != null && isProjectileWeapon(itemId)) {
                return WeaponCategory.PROJECTILE;
            }
        }
        
        // Default to physical for melee weapons
        return WeaponCategory.PHYSICAL;
    }
    
    /**
     * Check if an item ID indicates a magic weapon.
     */
    public static boolean isMagicWeapon(@Nullable final String itemId) {
        if (itemId == null) {
            return false;
        }
        final String lower = itemId.toLowerCase();
        for (final String pattern : MAGIC_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if an item ID indicates a projectile weapon.
     */
    public static boolean isProjectileWeapon(@Nullable final String itemId) {
        if (itemId == null) {
            return false;
        }
        final String lower = itemId.toLowerCase();
        for (final String pattern : PROJECTILE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get a user-friendly display name for a category.
     */
    @Nonnull
    public static String getDisplayName(@Nonnull final WeaponCategory category) {
        switch (category) {
            case PHYSICAL:
                return "Physical";
            case PROJECTILE:
                return "Projectile";
            case MAGIC:
                return "Magic";
            case RING:
                return "Ring";
            default:
                return "Unknown";
        }
    }
}
