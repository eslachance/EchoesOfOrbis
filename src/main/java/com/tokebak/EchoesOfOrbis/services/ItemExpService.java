package com.tokebak.EchoesOfOrbis.services;


import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.tokebak.EchoesOfOrbis.inventory.ItemTagUtil;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.services.effects.UpgradeOption;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    public static final String META_KEY_PENDING_UPGRADE_OPTIONS = "ItemExp_PendingUpgradeOptions";

    /**
     * Codec for storing unlocked effect IDs as string array.
     */
    private static final Codec<String[]> UNLOCKED_EFFECTS_CODEC = 
            new ArrayCodec<>(Codec.STRING, String[]::new);
    
    private static volatile ItemExpService instance;

    private final EchoesOfOrbisConfig config;
    private final WeaponEffectsService effectsService;

    /**
     * Set the global instance (called by EchoesOfOrbis during setup).
     * Used by ShowUpgradeSelectionInteraction which cannot receive constructor injection.
     */
    public static void setInstance(@Nonnull final ItemExpService itemExpService) {
        instance = itemExpService;
    }

    @Nullable
    public static ItemExpService getInstance() {
        return instance;
    }

    /**
     * Cache of pending XP that hasn't been persisted to weapon metadata yet.
     * XP is cached during combat to avoid interrupting abilities (like ultimates).
     * It gets flushed after a period of combat inactivity or on level up.
     * 
     * Key: playerUUID + ":" + hotbarSlot
     * Value: accumulated XP
     */
    private final Map<String, Double> pendingXpCache = new ConcurrentHashMap<>();
    
    public ItemExpService(
            @Nonnull final EchoesOfOrbisConfig config,
            @Nonnull final WeaponEffectsService effectsService
    ) {
        this.config = config;
        this.effectsService = effectsService;
    }
    
    /**
     * Get the cache key for pending XP (weapon hotbar slot).
     */
    public String getPendingXpKey(@Nonnull final PlayerRef playerRef, final byte slot) {
        return playerRef.getUuid().toString() + ":" + slot;
    }

    /**
     * Get the cache key for pending XP on a bauble ring (bauble container slot).
     */
    public String getPendingXpKeyForRing(@Nonnull final PlayerRef playerRef, final short baubleSlot) {
        return playerRef.getUuid().toString() + ":bauble:" + baubleSlot;
    }

    /**
     * Get the cache key for pending XP on an armor slot (0 = Head, 1 = Chest, 2 = Hands, 3 = Legs).
     */
    public String getPendingXpKeyForArmor(@Nonnull final PlayerRef playerRef, final short armorSlot) {
        return playerRef.getUuid().toString() + ":armor:" + armorSlot;
    }
    
    /**
     * Get the total XP for a weapon including any pending (not yet persisted) XP.
     */
    public double getTotalXpWithPending(@Nonnull final ItemStack weapon, @Nonnull final PlayerRef playerRef, final byte slot) {
        final double storedXp = this.getItemXp(weapon);
        final String key = getPendingXpKey(playerRef, slot);
        final Double pendingXp = this.pendingXpCache.get(key);
        return storedXp + (pendingXp != null ? pendingXp : 0.0);
    }
    
    /**
     * Add XP to the pending cache (not persisted yet).
     */
    public void addPendingXp(@Nonnull final PlayerRef playerRef, final byte slot, final double xp) {
        final String key = getPendingXpKey(playerRef, slot);
        this.pendingXpCache.merge(key, xp, Double::sum);
    }
    
    /**
     * Get the amount of pending XP for a player/slot.
     */
    public double getPendingXp(@Nonnull final PlayerRef playerRef, final byte slot) {
        final String key = getPendingXpKey(playerRef, slot);
        final Double pending = this.pendingXpCache.get(key);
        return pending != null ? pending : 0.0;
    }
    
    /**
     * Flush pending XP for a player's hotbar slot to the weapon and return the updated weapon.
     * 
     * @param weapon The current weapon
     * @param playerRef The player
     * @param slot The hotbar slot
     * @return Updated weapon with flushed XP, or original if no pending XP
     */
    @Nonnull
    public ItemStack flushPendingXp(
            @Nonnull final ItemStack weapon,
            @Nonnull final PlayerRef playerRef,
            final byte slot
    ) {
        final String key = getPendingXpKey(playerRef, slot);
        final Double pendingXp = this.pendingXpCache.remove(key);
        
        if (pendingXp == null || pendingXp <= 0) {
            return weapon; // No pending XP to flush
        }
        
        // Add the pending XP to the weapon
        return this.addXpToItem(weapon, pendingXp);
    }
    
    /**
     * Clear pending XP for a player's hotbar slot without applying it.
     */
    public void clearPendingXp(@Nonnull final PlayerRef playerRef, final byte slot) {
        final String key = getPendingXpKey(playerRef, slot);
        this.pendingXpCache.remove(key);
    }

    // ==================== RING XP (bauble container) ====================

    /**
     * Add XP to the pending cache for a bauble ring slot.
     */
    public void addPendingXpForRing(@Nonnull final PlayerRef playerRef, final short baubleSlot, final double xp) {
        final String key = getPendingXpKeyForRing(playerRef, baubleSlot);
        this.pendingXpCache.merge(key, xp, Double::sum);
    }

    /**
     * Get pending XP for a bauble ring slot.
     */
    public double getPendingXpForRing(@Nonnull final PlayerRef playerRef, final short baubleSlot) {
        final String key = getPendingXpKeyForRing(playerRef, baubleSlot);
        final Double pending = this.pendingXpCache.get(key);
        return pending != null ? pending : 0.0;
    }

    /**
     * Get total XP for a ring (stored + pending).
     */
    public double getTotalXpWithPendingForRing(
            @Nonnull final ItemStack ring,
            @Nonnull final PlayerRef playerRef,
            final short baubleSlot
    ) {
        final double storedXp = this.getItemXp(ring);
        final double pendingXp = this.getPendingXpForRing(playerRef, baubleSlot);
        return storedXp + pendingXp;
    }

    /**
     * Flush pending XP for a bauble ring slot into the ring and return the updated stack.
     */
    @Nonnull
    public ItemStack flushPendingXpForRing(
            @Nonnull final ItemStack ring,
            @Nonnull final PlayerRef playerRef,
            final short baubleSlot
    ) {
        final String key = getPendingXpKeyForRing(playerRef, baubleSlot);
        final Double pendingXp = this.pendingXpCache.remove(key);
        if (pendingXp == null || pendingXp <= 0) {
            return ring;
        }
        return this.addXpToItem(ring, pendingXp);
    }

    /**
     * Clear pending XP for a bauble ring slot without applying it.
     */
    public void clearPendingXpForRing(@Nonnull final PlayerRef playerRef, final short baubleSlot) {
        final String key = getPendingXpKeyForRing(playerRef, baubleSlot);
        this.pendingXpCache.remove(key);
    }

    // ==================== ARMOR XP (inventory armor container) ====================

    /**
     * Add XP to the pending cache for an armor slot (0 = Head, 1 = Chest, 2 = Hands, 3 = Legs).
     */
    public void addPendingXpForArmor(@Nonnull final PlayerRef playerRef, final short armorSlot, final double xp) {
        final String key = getPendingXpKeyForArmor(playerRef, armorSlot);
        this.pendingXpCache.merge(key, xp, Double::sum);
    }

    /**
     * Get pending XP for an armor slot.
     */
    public double getPendingXpForArmor(@Nonnull final PlayerRef playerRef, final short armorSlot) {
        final String key = getPendingXpKeyForArmor(playerRef, armorSlot);
        final Double pending = this.pendingXpCache.get(key);
        return pending != null ? pending : 0.0;
    }

    /**
     * Get total XP for an armor piece (stored + pending).
     */
    public double getTotalXpWithPendingForArmor(
            @Nonnull final ItemStack armorPiece,
            @Nonnull final PlayerRef playerRef,
            final short armorSlot
    ) {
        final double storedXp = this.getItemXp(armorPiece);
        final double pendingXp = this.getPendingXpForArmor(playerRef, armorSlot);
        return storedXp + pendingXp;
    }

    /**
     * Flush pending XP for an armor slot into the item and return the updated stack.
     */
    @Nonnull
    public ItemStack flushPendingXpForArmor(
            @Nonnull final ItemStack armorPiece,
            @Nonnull final PlayerRef playerRef,
            final short armorSlot
    ) {
        final String key = getPendingXpKeyForArmor(playerRef, armorSlot);
        final Double pendingXp = this.pendingXpCache.remove(key);
        if (pendingXp == null || pendingXp <= 0) {
            return armorPiece;
        }
        return this.addXpToItem(armorPiece, pendingXp);
    }

    /**
     * Clear pending XP for an armor slot without applying it.
     */
    public void clearPendingXpForArmor(@Nonnull final PlayerRef playerRef, final short armorSlot) {
        final String key = getPendingXpKeyForArmor(playerRef, armorSlot);
        this.pendingXpCache.remove(key);
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
     * Add multiple pending embues in one metadata write.
     */
    @Nonnull
    public ItemStack addPendingEmbues(@Nonnull final ItemStack item, final int count) {
        if (count <= 0) return item;
        final int current = this.getPendingEmbues(item);
        return item.withMetadata(META_KEY_PENDING_EMBUES, Codec.INTEGER, current + count);
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
     * Check if a specific effect is unlocked on this weapon.
     */
    public boolean isEffectUnlocked(@Nullable final ItemStack item, @Nonnull final WeaponEffectType type) {
        if (item == null) return false;
        final String[] effects = (String[]) item.getFromMetadataOrNull(META_KEY_UNLOCKED_EFFECTS, UNLOCKED_EFFECTS_CODEC);
        if (effects == null) return false;
        final String targetId = type.getId();
        for (final String id : effects) {
            if (targetId.equals(id)) return true;
        }
        return false;
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
     * Persisted upgrade options format: "effectTypeId:level" per option.
     * level 0 = NewEffectOption, level > 0 = BoostOption with that current level.
     */
    private static String[] serializeUpgradeOptions(@Nonnull final List<UpgradeOption> options) {
        final String[] out = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            final UpgradeOption opt = options.get(i);
            final int level = opt instanceof UpgradeOption.BoostOption b ? b.getCurrentLevel() : 0;
            out[i] = opt.getEffectType().getId() + ":" + level;
        }
        return out;
    }

    @Nullable
    private static List<UpgradeOption> deserializeUpgradeOptions(@Nullable final String[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        final List<UpgradeOption> options = new ArrayList<>();
        for (final String s : raw) {
            final int colon = s.indexOf(':');
            if (colon < 0) continue;
            final String effectId = s.substring(0, colon);
            final int level;
            try {
                level = Integer.parseInt(s.substring(colon + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            final WeaponEffectType type = WeaponEffectType.fromId(effectId);
            if (type == null) continue;
            if (level == 0) {
                options.add(new UpgradeOption.NewEffectOption(type));
            } else {
                options.add(new UpgradeOption.BoostOption(type, level));
            }
        }
        return options.isEmpty() ? null : options;
    }

    /**
     * Get persisted upgrade options from weapon metadata, or null if none.
     */
    @Nullable
    public List<UpgradeOption> getPendingUpgradeOptions(@Nullable final ItemStack weapon) {
        if (weapon == null) return null;
        final String[] raw = (String[]) weapon.getFromMetadataOrNull(META_KEY_PENDING_UPGRADE_OPTIONS, UNLOCKED_EFFECTS_CODEC);
        return deserializeUpgradeOptions(raw);
    }

    /**
     * Store upgrade options in weapon metadata.
     */
    @Nonnull
    public ItemStack setPendingUpgradeOptions(@Nonnull final ItemStack weapon, @Nonnull final List<UpgradeOption> options) {
        return weapon.withMetadata(META_KEY_PENDING_UPGRADE_OPTIONS, UNLOCKED_EFFECTS_CODEC, serializeUpgradeOptions(options));
    }

    /**
     * Clear persisted upgrade options from weapon metadata.
     */
    @Nonnull
    public ItemStack clearPendingUpgradeOptions(@Nonnull final ItemStack weapon) {
        return weapon.withMetadata(META_KEY_PENDING_UPGRADE_OPTIONS, UNLOCKED_EFFECTS_CODEC, null);
    }

    /**
     * Result of getOrCreatePendingUpgradeOptions.
     */
    public static final class UpgradeOptionsResult {
        @Nonnull public final List<UpgradeOption> options;
        /** Non-null if options were generated and weapon must be persisted. */
        @Nullable public final ItemStack weaponToPersist;

        public UpgradeOptionsResult(@Nonnull final List<UpgradeOption> options, @Nullable final ItemStack weaponToPersist) {
            this.options = options;
            this.weaponToPersist = weaponToPersist;
        }
    }

    /**
     * Get persisted upgrade options, or generate and store new ones if none.
     * When weaponToPersist is non-null, the caller must persist it to the inventory.
     */
    @Nonnull
    public UpgradeOptionsResult getOrCreatePendingUpgradeOptions(
            @Nonnull final ItemStack weapon,
            @Nonnull final WeaponCategory category,
            final int count
    ) {
        final List<UpgradeOption> cached = this.getPendingUpgradeOptions(weapon);
        if (cached != null && !cached.isEmpty()) {
            return new UpgradeOptionsResult(cached, null);
        }
        final List<UpgradeOption> generated = this.effectsService.getRandomUpgradeOptions(weapon, category, count);
        final ItemStack updated = this.setPendingUpgradeOptions(weapon, generated);
        return new UpgradeOptionsResult(generated, updated);
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

    /** Upper bound for level when there is no config cap (used in binary search). */
    private static final int NO_CAP_LEVEL_BOUND = 9999;

    /**
     * Calculate what level corresponds to a given amount of XP.
     * Inverse of getXpRequiredForLevel.
     */
    public int calculateLevelFromXp(final double totalXp) {
        if (totalXp <= 0) return 1;
        final int cap = this.getEffectiveMaxLevel();
        if (totalXp >= this.getXpRequiredForLevel(cap)) return cap;

        // Binary search: find lowest level L where getXpRequiredForLevel(L+1) > totalXp
        int lo = 1;
        int hi = cap;
        while (lo < hi) {
            final int mid = (lo + hi + 1) >>> 1;
            if (this.getXpRequiredForLevel(mid) <= totalXp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
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
     * Get the effective maximum level (config value, or NO_CAP_LEVEL_BOUND when no cap).
     */
    public int getMaxLevel() {
        return this.getEffectiveMaxLevel();
    }

    private int getEffectiveMaxLevel() {
        final int cfg = this.config.getMaxLevel();
        return cfg > 0 ? cfg : NO_CAP_LEVEL_BOUND;
    }

    /**
     * Check if a weapon is at max level. Always false when there is no level cap.
     */
    public boolean isAtMaxLevel(@Nonnull final ItemStack item) {
        final int cap = this.config.getMaxLevel();
        if (cap <= 0) return false;
        return this.getItemLevel(item) >= cap;
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
     * Effect stats are now static based on selected boosts - no auto-update on level.
     * Effects only change when the player selects a boost or new effect in the upgrade UI.
     */
    @Nonnull
    public ItemStack updateWeaponEffects(@Nonnull final ItemStack weapon, final int newLevel) {
        return weapon;
    }

    /**
     * Check if an item can gain XP (weapon, tool, armor, or bauble ring).
     * Only non-stackable items can gain XP.
     */
    public boolean canGainXp(@Nullable final ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }

        final var itemConfig = item.getItem();

        // Exclude stackable items
        if (itemConfig.getMaxStack() > 1) {
            return false;
        }

        // Allow bauble rings (tag Bauble_Ring) to gain XP and get upgrades
        if (ItemTagUtil.hasTag(item, "Bauble_Ring")) {
            return true;
        }

        // Allow armor to gain XP (from taking damage)
        if (itemConfig.getArmor() != null) {
            return true;
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

        if (this.isAtMaxLevel(item)) {
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
