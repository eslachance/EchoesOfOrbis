package com.tokebak.EchoesOfOrbis.services.effects;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/**
 * Represents a single effect instance stored on a weapon.
 * This is what gets serialized to the weapon's metadata.
 * 
 * Each weapon can have multiple WeaponEffectInstance objects,
 * stored as a list in the item's metadata.
 */
public class WeaponEffectInstance {
    
    /**
     * Codec for serializing/deserializing to item metadata.
     */
    public static final BuilderCodec<WeaponEffectInstance> CODEC = BuilderCodec
            .builder(WeaponEffectInstance.class, WeaponEffectInstance::new)
            .append(
                    new KeyedCodec<>("Type", Codec.STRING),
                    (instance, typeId) -> instance.typeId = typeId,
                    instance -> instance.typeId
            ).add()
            .append(
                    new KeyedCodec<>("Level", Codec.INTEGER),
                    (instance, level) -> instance.level = level,
                    instance -> instance.level
            ).add()
            .build();
    
    /**
     * The effect type ID (maps to WeaponEffectType enum).
     */
    private String typeId;
    
    /**
     * The level of this effect (1-based, affects strength).
     */
    private int level;
    
    /**
     * Default constructor for codec deserialization.
     */
    public WeaponEffectInstance() {
        this.typeId = "";
        this.level = 1;
    }
    
    /**
     * Create a new effect instance.
     * @param type The effect type
     * @param level The effect level (1+)
     */
    public WeaponEffectInstance(@Nonnull final WeaponEffectType type, final int level) {
        this.typeId = type.getId();
        this.level = Math.max(1, level);
    }
    
    /**
     * Get the effect type.
     * @return The effect type, or null if the type ID is invalid
     */
    public WeaponEffectType getType() {
        return WeaponEffectType.fromId(this.typeId);
    }
    
    /**
     * Get the effect type ID string.
     */
    public String getTypeId() {
        return this.typeId;
    }
    
    /**
     * Get the effect level.
     */
    public int getLevel() {
        return this.level;
    }
    
    /**
     * Set the effect level.
     */
    public void setLevel(final int level) {
        this.level = Math.max(1, level);
    }
    
    /**
     * Increase the effect level by 1.
     * @return The new level
     */
    public int incrementLevel() {
        return ++this.level;
    }
    
    /**
     * Create a copy with an incremented level.
     */
    @Nonnull
    public WeaponEffectInstance withIncrementedLevel() {
        final WeaponEffectType type = this.getType();
        if (type == null) {
            return new WeaponEffectInstance();
        }
        return new WeaponEffectInstance(type, this.level + 1);
    }
    
    @Override
    public String toString() {
        return "WeaponEffectInstance{type=" + this.typeId + ", level=" + this.level + "}";
    }
}
