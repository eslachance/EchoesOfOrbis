package com.tokebak.EchoesOfOrbis.services.effects;

import java.util.HashMap;
import java.util.Map;
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
    
    // ==================== WEAPON EFFECTS (physical, projectile, magic — not rings) ====================
    
    /**
     * Deals bonus damage as a percentage of the original hit.
     * Applied as a second damage instance.
     */
    DAMAGE_PERCENT("damage_percent", WeaponCategory.weapons()),
    
    /**
     * Heals the attacker for a percentage of damage dealt.
     */
    LIFE_LEECH("life_leech", WeaponCategory.weapons()),
    
    /**
     * Reduces durability loss on the weapon or tool.
     */
    DURABILITY_SAVE("durability_save", WeaponCategory.weaponsAndTools()),
    
    /**
     * Chance to deal critical damage (multiplied).
     */
    CRIT_CHANCE("crit_chance", WeaponCategory.weapons()),
    
    /**
     * Critical damage multiplier bonus.
     */
    CRIT_DAMAGE("crit_damage", WeaponCategory.weapons()),
    
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
     * Chance to slow the target's movement speed.
     * Only for physical/melee weapons.
     */
    SLOW_ON_HIT("slow_on_hit", WeaponCategory.melee()),
    
    /**
     * Chance to freeze the target (immobilize).
     * Only for physical/melee weapons.
     */
    FREEZE_ON_HIT("freeze_on_hit", WeaponCategory.melee()),
    
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
    
    // ==================== RING ONLY (bauble rings with tag Bauble_Ring) ====================

    /**
     * Bonus max stamina. Applied from rings in bauble container or from armor.
     */
    RING_STAMINA("ring_stamina", WeaponCategory.ringAndArmor()),

    /**
     * Bonus max health. Applied from rings in bauble container or from armor.
     */
    RING_HEALTH("ring_health", WeaponCategory.ringAndArmor()),

    /**
     * Bonus attack power (damage multiplier). Applied when player deals damage. Rings only (offensive).
     */
    RING_ATTACK_POWER("ring_attack_power", WeaponCategory.ring()),

    /**
     * Health regen (same idea as food effect e.g. Health Regen I). Rings only.
     */
    RING_HEALTH_REGEN("ring_health_regen", WeaponCategory.ring()),

    /**
     * Resist magic (damage reduction vs magic). Applied from rings or armor.
     */
    RING_RESIST_MAGIC("ring_resist_magic", WeaponCategory.ringAndArmor()),

    /**
     * Thorns: when the player takes damage, reflect damage back at the attacker.
     */
    RING_THORNS("ring_thorns", WeaponCategory.ringAndArmor()),

    /**
     * Signature energy boost: +1 extra signature energy per level added on every attack.
     */
    RING_SIGNATURE_ENERGY("ring_signature_energy", WeaponCategory.ringAndArmor()),

    // ==================== ARMOR ONLY (defensive resistance effects) ====================

    /**
     * Projectile resistance. Applied as stat modifier from equipped armor.
     */
    ARMOR_PROJECTILE_RESISTANCE("armor_projectile_resistance", WeaponCategory.armor()),

    /**
     * Physical resistance. Applied as stat modifier from equipped armor.
     */
    ARMOR_PHYSICAL_RESISTANCE("armor_physical_resistance", WeaponCategory.armor()),

    /**
     * Fire resistance. Applied as stat modifier from equipped armor.
     */
    ARMOR_FIRE_RESISTANCE("armor_fire_resistance", WeaponCategory.armor()),

    /**
     * General resistance (all damage types). Applied as stat modifier from equipped armor.
     */
    ARMOR_GENERAL_RESISTANCE("armor_general_resistance", WeaponCategory.armor()),

    // ==================== TOOL ONLY ====================

    /**
     * Bonus percent to items dropped when breaking blocks with this tool.
     */
    TOOL_DROP_BONUS("tool_drop_bonus", WeaponCategory.tools()),

    // ==================== LEGACY / GENERIC ====================

    /**
     * Modifies a player stat while the weapon is held.
     */
    PLAYER_STAT("player_stat", WeaponCategory.weapons());

    private static final Map<String, WeaponEffectType> BY_ID = new HashMap<>();
    static {
        for (final WeaponEffectType t : values()) {
            BY_ID.put(t.id, t);
        }
    }

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
     * Check if this effect can apply to a specific weapon category.
     */
    public boolean appliesTo(final WeaponCategory category) {
        return this.applicableCategories.contains(category);
    }
    
    /**
     * Look up an effect type by its string ID.
     * @param id The effect ID
     * @return The effect type, or null if not found
     */
    public static WeaponEffectType fromId(final String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
