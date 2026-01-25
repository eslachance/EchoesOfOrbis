package com.tokebak.EchoesOfOrbis.services.effects;

/**
 * Enum defining all available weapon effect types.
 * Each type has a unique string ID for serialization to metadata.
 * 
 * To add a new effect:
 * 1. Add the enum value here
 * 2. Create a processor class implementing EffectProcessor
 * 3. Register the processor in WeaponEffectsService
 */
public enum WeaponEffectType {
    
    /**
     * Deals bonus damage as a percentage of the original hit.
     * Applied as a second damage instance.
     */
    DAMAGE_PERCENT("damage_percent"),
    
    /**
     * Adds flat bonus damage to attacks.
     * Applied as a second damage instance.
     */
    DAMAGE_FLAT("damage_flat"),
    
    /**
     * Heals the attacker for a percentage of damage dealt.
     */
    LIFE_LEECH("life_leech"),
    
    /**
     * Chance to apply fire damage over time.
     */
    FIRE_ON_HIT("fire_on_hit"),
    
    /**
     * Chance to apply poison damage over time.
     */
    POISON_ON_HIT("poison_on_hit"),
    
    /**
     * Modifies a player stat while the weapon is held.
     */
    PLAYER_STAT("player_stat"),
    
    /**
     * Chance to deal critical damage (multiplied).
     */
    CRIT_CHANCE("crit_chance");
    
    private final String id;
    
    WeaponEffectType(final String id) {
        this.id = id;
    }
    
    /**
     * Get the string ID used for serialization.
     */
    public String getId() {
        return this.id;
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
