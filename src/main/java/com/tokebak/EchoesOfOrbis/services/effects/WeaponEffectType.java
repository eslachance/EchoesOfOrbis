package com.tokebak.EchoesOfOrbis.services.effects;

import java.util.Set;

/**
 * Enum defining all available weapon effect types.
 * Each type has:
 * - A unique string ID for serialization
 * - A set of weapon categories it can apply to
 * 
 * To add a new effect:
 * 1. Add the enum value here with appropriate categories
 * 2. Create a processor class implementing EffectProcessor
 * 3. Register the processor in WeaponEffectsService
 */
public enum WeaponEffectType {
    
    // ==================== UNIVERSAL EFFECTS (all weapon types) ====================
    
    /**
     * Deals bonus damage as a percentage of the original hit.
     * Applied as a second damage instance.
     */
    DAMAGE_PERCENT("damage_percent", WeaponCategory.all()),
    
    /**
     * Adds flat bonus damage to attacks.
     * Applied as a second damage instance.
     */
    DAMAGE_FLAT("damage_flat", WeaponCategory.all()),
    
    /**
     * Heals the attacker for a percentage of damage dealt.
     */
    LIFE_LEECH("life_leech", WeaponCategory.all()),
    
    /**
     * Reduces durability loss on the weapon.
     */
    DURABILITY_SAVE("durability_save", WeaponCategory.all()),
    
    /**
     * Chance to deal critical damage (multiplied).
     */
    CRIT_CHANCE("crit_chance", WeaponCategory.all()),
    
    /**
     * Critical damage multiplier bonus.
     */
    CRIT_DAMAGE("crit_damage", WeaponCategory.all()),
    
    // ==================== PHYSICAL (melee) ONLY ====================
    
    /**
     * Chance to apply fire damage over time.
     * Only for physical/melee weapons.
     */
    FIRE_ON_HIT("fire_on_hit", WeaponCategory.melee()),
    
    /**
     * Chance to apply poison damage over time.
     * Only for physical/melee weapons.
     */
    POISON_ON_HIT("poison_on_hit", WeaponCategory.melee()),
    
    /**
     * Chance to stun/stagger the target.
     * Only for physical/melee weapons.
     */
    STUN_ON_HIT("stun_on_hit", WeaponCategory.melee()),
    
    /**
     * Bonus damage to bleeding targets.
     * Only for physical/melee weapons.
     */
    BLEEDING("bleeding", WeaponCategory.melee()),
    
    // ==================== PROJECTILE ONLY ====================
    
    /**
     * Chance to not consume ammo.
     * Only for projectile weapons (bows, guns).
     */
    AMMO_SAVE("ammo_save", WeaponCategory.of(WeaponCategory.PROJECTILE)),
    
    /**
     * Chance to fire an additional projectile.
     * Only for projectile weapons.
     */
    MULTISHOT("multishot", WeaponCategory.of(WeaponCategory.PROJECTILE)),
    
    /**
     * Increased projectile velocity/range.
     * Only for projectile weapons.
     */
    PROJECTILE_SPEED("projectile_speed", WeaponCategory.of(WeaponCategory.PROJECTILE)),
    
    /**
     * Projectiles pierce through enemies.
     * Only for projectile weapons.
     */
    PIERCING("piercing", WeaponCategory.of(WeaponCategory.PROJECTILE)),
    
    // ==================== MAGIC ONLY ====================
    
    /**
     * Reduced mana/energy cost.
     * Only for magic weapons.
     */
    MANA_COST_REDUCTION("mana_cost_reduction", WeaponCategory.of(WeaponCategory.MAGIC)),
    
    /**
     * Spells chain to nearby enemies.
     * Only for magic weapons.
     */
    CHAIN_SPELL("chain_spell", WeaponCategory.of(WeaponCategory.MAGIC)),
    
    /**
     * Increased spell area of effect.
     * Only for magic weapons.
     */
    SPELL_AREA("spell_area", WeaponCategory.of(WeaponCategory.MAGIC)),
    
    /**
     * Faster spell cooldowns.
     * Only for magic weapons.
     */
    COOLDOWN_REDUCTION("cooldown_reduction", WeaponCategory.of(WeaponCategory.MAGIC)),
    
    // ==================== RANGED (Projectile + Magic) ====================
    
    /**
     * Homing/tracking on targets.
     * For both projectile and magic weapons.
     */
    HOMING("homing", WeaponCategory.ranged()),
    
    // ==================== MELEE (Physical only for now) ====================
    
    /**
     * Increased attack speed.
     * For physical weapons.
     */
    ATTACK_SPEED("attack_speed", WeaponCategory.melee()),
    
    /**
     * Increased knockback.
     * For physical weapons.
     */
    KNOCKBACK("knockback", WeaponCategory.melee()),
    
    // ==================== LEGACY / GENERIC ====================
    
    /**
     * Modifies a player stat while the weapon is held.
     */
    PLAYER_STAT("player_stat", WeaponCategory.all());
    
    private final String id;
    private final Set<WeaponCategory> applicableCategories;
    
    WeaponEffectType(final String id, final Set<WeaponCategory> categories) {
        this.id = id;
        this.applicableCategories = categories;
    }
    
    /**
     * Get the string ID used for serialization.
     */
    public String getId() {
        return this.id;
    }
    
    /**
     * Get the weapon categories this effect can apply to.
     */
    public Set<WeaponCategory> getApplicableCategories() {
        return this.applicableCategories;
    }
    
    /**
     * Check if this effect can apply to a specific weapon category.
     */
    public boolean appliesTo(final WeaponCategory category) {
        return this.applicableCategories.contains(category);
    }
    
    /**
     * Check if this effect applies to all weapon categories.
     */
    public boolean isUniversal() {
        return this.applicableCategories.size() == WeaponCategory.values().length;
    }
    
    /**
     * Look up an effect type by its string ID.
     * @param id The effect ID
     * @return The effect type, or null if not found
     */
    public static WeaponEffectType fromId(final String id) {
        for (final WeaponEffectType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
