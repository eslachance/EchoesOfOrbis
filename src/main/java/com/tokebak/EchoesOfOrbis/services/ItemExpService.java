package com.tokebak.EchoesOfOrbis.services;


import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Service that handles all item experience logic.
 *
 * Responsibilities:
 * - Calculate XP gains from damage
 * - Read/write XP to item metadata
 * - Calculate levels from XP
 * - Update items in inventory
 * - Coordinate with WeaponEffectsService for level-up bonuses
 */
public class ItemExpService {

    // Metadata keys stored on items
    public static final String META_KEY_XP = "ItemExp_XP";
    public static final String META_KEY_LEVEL = "ItemExp_Level";

    private final EchoesOfOrbisConfig config;
    private final WeaponEffectsService effectsService;

    public ItemExpService(
            @Nonnull final EchoesOfOrbisConfig config,
            @Nonnull final WeaponEffectsService effectsService
    ) {
        this.config = config;
        this.effectsService = effectsService;
    }
    
    /**
     * Get the effects service for external use.
     */
    @Nonnull
    public WeaponEffectsService getEffectsService() {
        return this.effectsService;
    }

    /**
     * Calculate how much XP should be gained from dealing damage.
     * Formula: damage * xpPerDamage * xpMultiplier
     */
    public double calculateXpFromDamage(final float damageDealt) {
        if (damageDealt <= 0) {
            return 0.0;
        }
        return damageDealt * this.config.getXpPerDamage() * this.config.getXpMultiplier();
    }

    /**
     * Get the current XP stored on an item.
     * Returns 0.0 if the item has no XP metadata.
     */
    public double getItemXp(@Nullable final ItemStack item) {
        if (item == null) {
            return 0.0;
        }

        // getFromMetadataOrNull returns null if key doesn't exist
        final Double xp = (Double) item.getFromMetadataOrNull(META_KEY_XP, Codec.DOUBLE);
        return xp != null ? xp : 0.0;
    }

    /**
     * Get the current level of an item based on its XP.
     * We calculate this from XP rather than storing it separately
     * to ensure consistency.
     */
    public int getItemLevel(@Nullable final ItemStack item) {
        if (item == null) {
            return 1;
        }

        final double xp = this.getItemXp(item);
        return this.calculateLevelFromXp(xp);
    }

    /**
     * Calculate what level corresponds to a given amount of XP.
     * Inverse of getXpRequiredForLevel.
     */
    public int calculateLevelFromXp(final double totalXp) {
        if (totalXp <= 0) {
            return 1;
        }

        // Binary search to find the level
        // This is more reliable than inverting the formula mathematically
        int level = 1;
        while (level < this.config.getMaxLevel()) {
            final double xpForNextLevel = this.getXpRequiredForLevel(level + 1);
            if (totalXp < xpForNextLevel) {
                break;
            }
            level++;
        }

        return level;
    }

    /**
     * Get the total XP required to reach a specific level.
     * Formula: levelBaseXP * (level ^ levelScaling)
     *
     * Example with defaults (baseXP=100, scaling=1.5):
     * Level 2: 100 * 2^1.5 = 283 XP
     * Level 3: 100 * 3^1.5 = 520 XP
     * Level 10: 100 * 10^1.5 = 3162 XP
     */
    public double getXpRequiredForLevel(final int level) {
        if (level <= 1) {
            return 0.0;
        }
        return this.config.getLevelBaseXP() * Math.pow(level, this.config.getLevelScaling());
    }

    /**
     * Get XP needed to go from current level to next level.
     */
    public double getXpToNextLevel(final int currentLevel) {
        if (currentLevel >= this.config.getMaxLevel()) {
            return 0.0; // Already at max
        }
        return this.getXpRequiredForLevel(currentLevel + 1) - this.getXpRequiredForLevel(currentLevel);
    }

    /**
     * Create a new ItemStack with updated XP metadata.
     * Remember: ItemStack is immutable, so this returns a NEW item.
     */
    @Nonnull
    public ItemStack addXpToItem(@Nonnull final ItemStack item, final double xpToAdd) {
        final double currentXp = this.getItemXp(item);
        final double newXp = currentXp + xpToAdd;

        // withMetadata creates a new ItemStack with the updated metadata
        return item.withMetadata(META_KEY_XP, Codec.DOUBLE, newXp);
    }
    
    /**
     * Update a weapon's effects based on its current level.
     * Called after XP is added and a level-up occurs.
     * 
     * @param weapon The weapon to update
     * @param newLevel The weapon's new level
     * @return New ItemStack with updated effects
     */
    @Nonnull
    public ItemStack updateWeaponEffects(@Nonnull final ItemStack weapon, final int newLevel) {
        // Update the DAMAGE_PERCENT effect to match weapon level
        // This gives +5% damage per level (configured in WeaponEffectsService)
        return this.effectsService.updateDamagePercentEffect(weapon, newLevel);
    }

    /**
     * Add XP to the player's currently held weapon and update it in their inventory.
     * Also updates weapon effects on level up.
     *
     * @param playerRef The player reference
     * @param inventory The player's inventory
     * @param xpToAdd Amount of XP to add
     * @return The LevelUpResult with before/after levels and success status
     */
    @Nonnull
    public LevelUpResult addXpToHeldWeapon(
            @Nonnull final PlayerRef playerRef,
            @Nonnull final Inventory inventory,
            final double xpToAdd
    ) {
        // Get the currently held item
        final ItemStack currentWeapon = inventory.getActiveHotbarItem();
        if (currentWeapon == null) {
            return LevelUpResult.failure(); // Nothing in hand
        }

        // Get the slot index so we can replace the item
        final byte slot = inventory.getActiveHotbarSlot();
        if (slot == -1) {
            return LevelUpResult.failure(); // Invalid slot
        }

        // Get level before adding XP
        final int levelBefore = this.getItemLevel(currentWeapon);

        // Create new item with added XP
        ItemStack updatedWeapon = this.addXpToItem(currentWeapon, xpToAdd);

        // Calculate new level
        final int levelAfter = this.getItemLevel(updatedWeapon);

        // If weapon leveled up, update its effects
        if (levelAfter > levelBefore) {
            updatedWeapon = this.updateWeaponEffects(updatedWeapon, levelAfter);
            System.out.println(String.format(
                    "[ItemExp] Weapon leveled up! %d -> %d | Effects: %s",
                    levelBefore,
                    levelAfter,
                    this.effectsService.getEffectsSummary(updatedWeapon)
            ));
        }

        // Replace the item in the hotbar
        final ItemContainer hotbar = inventory.getHotbar();
        hotbar.setItemStackForSlot((short) slot, updatedWeapon);

        return new LevelUpResult(true, levelBefore, levelAfter);
    }
    
    /**
     * Result of adding XP to a weapon, including level change info.
     */
    public static class LevelUpResult {
        private final boolean success;
        private final int levelBefore;
        private final int levelAfter;
        
        public LevelUpResult(final boolean success, final int levelBefore, final int levelAfter) {
            this.success = success;
            this.levelBefore = levelBefore;
            this.levelAfter = levelAfter;
        }
        
        public static LevelUpResult failure() {
            return new LevelUpResult(false, 0, 0);
        }
        
        public boolean isSuccess() {
            return this.success;
        }
        
        public int getLevelBefore() {
            return this.levelBefore;
        }
        
        public int getLevelAfter() {
            return this.levelAfter;
        }
        
        public boolean didLevelUp() {
            return this.success && this.levelAfter > this.levelBefore;
        }
    }

    /**
     * Check if an item can gain XP (is it a weapon/tool that should level up?)
     * Only non-stackable weapons and tools can gain XP.
     * Excludes arrows, bullets, and other projectile ammo (which stack).
     */
    public boolean canGainXp(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        final var itemConfig = item.getItem();

        // Exclude stackable items - ammo like arrows/bullets stack, real weapons don't
        // maxStack > 1 means it's ammo or consumable, not a real weapon
        if (itemConfig.getMaxStack() > 1) {
            return false;
        }

        // Allow weapons and tools to gain XP
        return itemConfig.getWeapon() != null || itemConfig.getTool() != null;
    }

    /**
     * Get a formatted string showing item XP progress.
     * Example: "Level 5 | 450/520 XP (87%)"
     */
    @Nonnull
    public String getProgressString(@Nonnull final ItemStack item) {
        final double xp = this.getItemXp(item);
        final int level = this.calculateLevelFromXp(xp);

        if (level >= this.config.getMaxLevel()) {
            return String.format("Level %d (MAX)", level);
        }

        final double xpForCurrentLevel = this.getXpRequiredForLevel(level);
        final double xpForNextLevel = this.getXpRequiredForLevel(level + 1);
        final double xpIntoLevel = xp - xpForCurrentLevel;
        final double xpNeeded = xpForNextLevel - xpForCurrentLevel;
        final int percent = (int) ((xpIntoLevel / xpNeeded) * 100);

        return String.format("Level %d | %.0f/%.0f XP (%d%%)", level, xpIntoLevel, xpNeeded, percent);
    }
}
