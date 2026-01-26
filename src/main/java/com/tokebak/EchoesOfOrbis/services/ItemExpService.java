package com.tokebak.EchoesOfOrbis.services;


import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    public static final String META_KEY_PENDING_EMBUES = "ItemExp_PendingEmbues";
    public static final String META_KEY_UNLOCKED_EFFECTS = "ItemExp_UnlockedEffects";
    
    /**
     * Codec for storing unlocked effect IDs as string array.
     */
    private static final Codec<String[]> UNLOCKED_EFFECTS_CODEC = 
            new ArrayCodec<>(Codec.STRING, String[]::new);
    
    /**
     * Milestone levels where players can select an embue.
     * At each of these levels, a pending embue is added.
     */
    public static final int[] EMBUE_MILESTONES = {5, 10, 15, 20, 25};

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
    
    // ==================== EMBUE SYSTEM ====================
    
    /**
     * Check if a level is a milestone level where an embue is awarded.
     */
    public static boolean isMilestoneLevel(final int level) {
        for (final int milestone : EMBUE_MILESTONES) {
            if (level == milestone) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the number of pending embues (embue selections waiting to be made).
     */
    public int getPendingEmbues(@Nullable final ItemStack item) {
        if (item == null) {
            return 0;
        }
        final Integer pending = (Integer) item.getFromMetadataOrNull(META_KEY_PENDING_EMBUES, Codec.INTEGER);
        return pending != null ? pending : 0;
    }
    
    /**
     * Add a pending embue to the weapon.
     * Returns a new ItemStack with incremented pending count.
     */
    @Nonnull
    public ItemStack addPendingEmbue(@Nonnull final ItemStack item) {
        final int current = this.getPendingEmbues(item);
        return item.withMetadata(META_KEY_PENDING_EMBUES, Codec.INTEGER, current + 1);
    }
    
    /**
     * Consume (decrement) a pending embue after selection.
     * Returns a new ItemStack with decremented pending count.
     */
    @Nonnull
    public ItemStack consumePendingEmbue(@Nonnull final ItemStack item) {
        final int current = this.getPendingEmbues(item);
        final int newCount = Math.max(0, current - 1);
        return item.withMetadata(META_KEY_PENDING_EMBUES, Codec.INTEGER, newCount);
    }
    
    /**
     * Get the list of unlocked effect type IDs for this weapon.
     */
    @Nonnull
    public List<String> getUnlockedEffectIds(@Nullable final ItemStack item) {
        if (item == null) {
            return new ArrayList<>();
        }
        final String[] effects = (String[]) item.getFromMetadataOrNull(META_KEY_UNLOCKED_EFFECTS, UNLOCKED_EFFECTS_CODEC);
        if (effects == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(effects));
    }
    
    /**
     * Get the list of unlocked effect types for this weapon.
     */
    @Nonnull
    public List<WeaponEffectType> getUnlockedEffects(@Nullable final ItemStack item) {
        final List<String> ids = this.getUnlockedEffectIds(item);
        final List<WeaponEffectType> types = new ArrayList<>();
        for (final String id : ids) {
            final WeaponEffectType type = WeaponEffectType.fromId(id);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }
    
    /**
     * Check if a specific effect is unlocked on this weapon.
     */
    public boolean isEffectUnlocked(@Nullable final ItemStack item, @Nonnull final WeaponEffectType type) {
        final List<String> ids = this.getUnlockedEffectIds(item);
        return ids.contains(type.getId());
    }
    
    /**
     * Unlock an effect on a weapon.
     * Returns a new ItemStack with the effect added to the unlocked list.
     */
    @Nonnull
    public ItemStack unlockEffect(@Nonnull final ItemStack item, @Nonnull final WeaponEffectType type) {
        final List<String> ids = this.getUnlockedEffectIds(item);
        
        // Don't add duplicates
        if (ids.contains(type.getId())) {
            return item;
        }
        
        ids.add(type.getId());
        return item.withMetadata(
                META_KEY_UNLOCKED_EFFECTS, 
                UNLOCKED_EFFECTS_CODEC, 
                ids.toArray(new String[0])
        );
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
     * Formula: levelBaseXP * (level - 1)^levelScaling
     * 
     * This formula ensures:
     * - Level 2 requires exactly baseXP (quick first level up!)
     * - Each subsequent level requires significantly more XP
     * - The curve is steeper at higher levels
     *
     * Example with defaults (baseXP=100, scaling=2.5):
     * Level 2: 100 * 1^2.5 = 100 XP (fast!)
     * Level 3: 100 * 2^2.5 = 566 XP
     * Level 4: 100 * 3^2.5 = 1,558 XP
     * Level 5: 100 * 4^2.5 = 3,200 XP
     * Level 10: 100 * 9^2.5 = 24,300 XP
     * 
     * XP to advance (gaps):
     * 1→2: 100 | 2→3: 466 | 3→4: 992 | 4→5: 1,642
     */
    public double getXpRequiredForLevel(final int level) {
        if (level <= 1) {
            return 0.0;
        }
        // Use (level - 1) so level 2 = baseXP * 1^scaling = baseXP
        return this.config.getLevelBaseXP() * Math.pow(level - 1, this.config.getLevelScaling());
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
     * DAMAGE_PERCENT is always automatically updated.
     * Other effects (like DURABILITY_SAVE) are only updated if they've been
     * unlocked via the embue selection system.
     * 
     * @param weapon The weapon to update
     * @param newLevel The weapon's new level
     * @return New ItemStack with updated effects
     */
    @Nonnull
    public ItemStack updateWeaponEffects(@Nonnull final ItemStack weapon, final int newLevel) {
        // Always apply DAMAGE_PERCENT (the base damage scaling)
        ItemStack updated = this.effectsService.updateDamagePercentEffect(weapon, newLevel);
        
        // Apply other effects only if they've been unlocked via embue selection
        final List<WeaponEffectType> unlockedEffects = this.getUnlockedEffects(updated);
        for (final WeaponEffectType effectType : unlockedEffects) {
            // Skip DAMAGE_PERCENT since it's already applied
            if (effectType == WeaponEffectType.DAMAGE_PERCENT) {
                continue;
            }
            
            // Update the effect based on weapon level
            updated = this.effectsService.updateEffectForLevel(updated, effectType, newLevel);
        }
        
        return updated;
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
        
        // Skip XP gain if already at max level
        if (levelBefore >= this.config.getMaxLevel()) {
            return new LevelUpResult(true, levelBefore, levelBefore); // Success but no change
        }

        // Create new item with added XP
        ItemStack updatedWeapon = this.addXpToItem(currentWeapon, xpToAdd);

        // Calculate new level
        final int levelAfter = this.getItemLevel(updatedWeapon);

        // If weapon leveled up, update its effects and check for milestones
        if (levelAfter > levelBefore) {
            updatedWeapon = this.updateWeaponEffects(updatedWeapon, levelAfter);
            
            // Check if any milestone levels were crossed (can level up multiple times at once)
            int newEmbuesToAdd = 0;
            for (int level = levelBefore + 1; level <= levelAfter; level++) {
                if (isMilestoneLevel(level)) {
                    newEmbuesToAdd++;
                }
            }
            
            // Add pending embues for each milestone crossed
            for (int i = 0; i < newEmbuesToAdd; i++) {
                updatedWeapon = this.addPendingEmbue(updatedWeapon);
            }
            
            final int pendingEmbues = this.getPendingEmbues(updatedWeapon);
            System.out.println(String.format(
                    "[ItemExp] Weapon leveled up! %d -> %d | Effects: %s | Pending Embues: %d",
                    levelBefore,
                    levelAfter,
                    this.effectsService.getEffectsSummary(updatedWeapon),
                    pendingEmbues
            ));
            
            if (newEmbuesToAdd > 0) {
                System.out.println(String.format(
                        "[ItemExp] Milestone reached! +%d embue(s) available. Use /eoo to select.",
                        newEmbuesToAdd
                ));
            }
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
