package com.tokebak.EchoesOfOrbis.services.effects;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DamagePercentProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DurabilitySaveProcessor;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central service for managing weapon effects.
 * 
 * Responsibilities:
 * - Register and store effect definitions (how effects behave)
 * - Register effect processors (code that applies effects)
 * - Read/write effect instances from weapon metadata
 * - Apply effects at appropriate times (on damage, on equip, etc.)
 */
public class WeaponEffectsService {
    
    /**
     * Metadata key for storing effects on weapons.
     */
    public static final String META_KEY_EFFECTS = "ItemExp_Effects";
    
    /**
     * Codec for serializing effect lists to metadata.
     */
    private static final Codec<WeaponEffectInstance[]> EFFECTS_CODEC = 
            new ArrayCodec<>(WeaponEffectInstance.CODEC, WeaponEffectInstance[]::new);
    
    /**
     * Global definitions for each effect type.
     */
    private final Map<WeaponEffectType, WeaponEffectDefinition> definitions;
    
    /**
     * Processors for each effect type.
     */
    private final Map<WeaponEffectType, EffectProcessor> processors;
    
    public WeaponEffectsService() {
        this.definitions = new EnumMap<>(WeaponEffectType.class);
        this.processors = new EnumMap<>(WeaponEffectType.class);
        
        // Register default definitions and processors
        this.registerDefaults();
    }
    
    /**
     * Register the default effect definitions and processors.
     */
    private void registerDefaults() {
        // DAMAGE_PERCENT: Bonus damage scaling from 5% to 100% over levels 2-25
        // Effect level 1 (weapon level 2): 5%
        // Effect level 24 (weapon level 25): 100%
        this.registerDefinition(
                WeaponEffectDefinition.builder(WeaponEffectType.DAMAGE_PERCENT)
                        .baseValue(0.05)       // 5% at effect level 1
                        .valuePerLevel(0.0413) // +4.13% per level to reach 100% at level 25
                        .maxValue(1.0)         // Cap at 100% bonus
                        .maxLevel(24)
                        .description("+{value} damage")
                        .build()
        );
        this.registerProcessor(WeaponEffectType.DAMAGE_PERCENT, new DamagePercentProcessor());
        
        // DAMAGE_FLAT: Add flat damage
        this.registerDefinition(
                WeaponEffectDefinition.builder(WeaponEffectType.DAMAGE_FLAT)
                        .baseValue(1.0)       // +1 damage at effect level 1
                        .valuePerLevel(1.0)   // +1 per additional level
                        .maxValue(50.0)       // Cap at +50 damage
                        .maxLevel(20)
                        .description("+{value} flat damage")
                        .build()
        );
        // Processor for DAMAGE_FLAT would be similar to DAMAGE_PERCENT
        
        // LIFE_LEECH: Heal percentage of damage dealt
        this.registerDefinition(
                WeaponEffectDefinition.builder(WeaponEffectType.LIFE_LEECH)
                        .baseValue(0.02)      // 2% at effect level 1
                        .valuePerLevel(0.02)  // +2% per level
                        .maxValue(0.25)       // Cap at 25%
                        .maxLevel(10)
                        .description("Heal {value} of damage dealt")
                        .build()
        );
        
        // DURABILITY_SAVE: Chance to not lose durability on hit
        // Scales from 10% to 100% over levels 2-25
        // Effect level 1 (weapon level 2): 10%
        // Effect level 24 (weapon level 25): 100%
        this.registerDefinition(
                WeaponEffectDefinition.builder(WeaponEffectType.DURABILITY_SAVE)
                        .baseValue(0.10)       // 10% at effect level 1
                        .valuePerLevel(0.0391) // +3.91% per level to reach 100% at level 25
                        .maxValue(1.0)         // Cap at 100%
                        .maxLevel(24)
                        .description("{value} chance to save durability")
                        .build()
        );
        this.registerProcessor(WeaponEffectType.DURABILITY_SAVE, new DurabilitySaveProcessor());
        
        // More effects can be registered here or in configuration
    }
    
    /**
     * Register an effect definition.
     */
    public void registerDefinition(@Nonnull final WeaponEffectDefinition definition) {
        this.definitions.put(definition.getType(), definition);
    }
    
    /**
     * Register an effect processor.
     */
    public void registerProcessor(
            @Nonnull final WeaponEffectType type,
            @Nonnull final EffectProcessor processor
    ) {
        this.processors.put(type, processor);
    }
    
    /**
     * Get the definition for an effect type.
     */
    @Nullable
    public WeaponEffectDefinition getDefinition(@Nonnull final WeaponEffectType type) {
        return this.definitions.get(type);
    }
    
    /**
     * Get the processor for an effect type.
     */
    @Nullable
    public EffectProcessor getProcessor(@Nonnull final WeaponEffectType type) {
        return this.processors.get(type);
    }
    
    // ==================== Metadata Read/Write ====================
    
    /**
     * Get all effect instances from a weapon's metadata.
     * @param weapon The weapon ItemStack
     * @return List of effect instances (empty if none)
     */
    @Nonnull
    public List<WeaponEffectInstance> getEffects(@Nullable final ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return new ArrayList<>();
        }
        
        final WeaponEffectInstance[] effects = (WeaponEffectInstance[]) weapon.getFromMetadataOrNull(
                META_KEY_EFFECTS,
                EFFECTS_CODEC
        );
        
        if (effects == null || effects.length == 0) {
            return new ArrayList<>();
        }
        
        // Convert to mutable list
        final List<WeaponEffectInstance> result = new ArrayList<>(effects.length);
        for (final WeaponEffectInstance effect : effects) {
            if (effect != null && effect.getType() != null) {
                result.add(effect);
            }
        }
        return result;
    }
    
    /**
     * Get a specific effect instance from a weapon.
     */
    @Nullable
    public WeaponEffectInstance getEffect(
            @Nullable final ItemStack weapon,
            @Nonnull final WeaponEffectType type
    ) {
        final List<WeaponEffectInstance> effects = this.getEffects(weapon);
        for (final WeaponEffectInstance effect : effects) {
            if (effect.getType() == type) {
                return effect;
            }
        }
        return null;
    }
    
    /**
     * Check if a weapon has a specific effect.
     */
    public boolean hasEffect(
            @Nullable final ItemStack weapon,
            @Nonnull final WeaponEffectType type
    ) {
        return this.getEffect(weapon, type) != null;
    }
    
    /**
     * Add or update an effect on a weapon.
     * Returns a new ItemStack with the updated effects.
     * 
     * @param weapon The weapon to modify
     * @param effect The effect to add/update
     * @return New ItemStack with updated metadata
     */
    @Nonnull
    public ItemStack setEffect(
            @Nonnull final ItemStack weapon,
            @Nonnull final WeaponEffectInstance effect
    ) {
        final List<WeaponEffectInstance> effects = this.getEffects(weapon);
        
        // Check if effect already exists
        boolean found = false;
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i).getType() == effect.getType()) {
                effects.set(i, effect);
                found = true;
                break;
            }
        }
        
        // Add if not found
        if (!found) {
            effects.add(effect);
        }
        
        // Save back to metadata
        return weapon.withMetadata(
                META_KEY_EFFECTS,
                EFFECTS_CODEC,
                effects.toArray(new WeaponEffectInstance[0])
        );
    }
    
    /**
     * Add DAMAGE_PERCENT effect at level based on weapon level.
     * This is the default effect added on level up.
     * 
     * Effect level = weaponLevel - 1, so:
     * - Weapon level 1 → Effect level 0 → 0% bonus (no effect)
     * - Weapon level 2 → Effect level 1 → 5% bonus
     * - Weapon level 3 → Effect level 2 → 10% bonus
     * - etc.
     * 
     * @param weapon The weapon
     * @param weaponLevel The weapon's current level
     * @return New ItemStack with updated effect
     */
    @Nonnull
    public ItemStack updateDamagePercentEffect(
            @Nonnull final ItemStack weapon,
            final int weaponLevel
    ) {
        // Effect level = weaponLevel - 1 (level 1 weapons have no bonus)
        final int effectLevel = weaponLevel - 1;
        
        // Don't add effect if level would be 0 or negative
        if (effectLevel < 1) {
            return weapon;
        }
        
        final WeaponEffectInstance effect = new WeaponEffectInstance(
                WeaponEffectType.DAMAGE_PERCENT,
                effectLevel
        );
        return this.setEffect(weapon, effect);
    }
    
    /**
     * TEMPORARY: Add DURABILITY_SAVE effect for testing.
     * 
     * This is added starting at weapon level 2:
     * - Weapon level 2 → Effect level 1 → 5% save chance
     * - Weapon level 3 → Effect level 2 → 6% save chance
     * - etc. (+1% per level)
     * 
     * @param weapon The weapon
     * @param weaponLevel The weapon's current level
     * @return New ItemStack with updated effect
     */
    @Nonnull
    public ItemStack updateDurabilitySaveEffect(
            @Nonnull final ItemStack weapon,
            final int weaponLevel
    ) {
        // Effect level = weaponLevel - 1 (level 1 weapons have no bonus)
        final int effectLevel = weaponLevel - 1;
        
        // Don't add effect if level would be 0 or negative
        if (effectLevel < 1) {
            return weapon;
        }
        
        final WeaponEffectInstance effect = new WeaponEffectInstance(
                WeaponEffectType.DURABILITY_SAVE,
                effectLevel
        );
        return this.setEffect(weapon, effect);
    }
    
    /**
     * TEMPORARY: Update all standard effects for testing.
     * Called on level up to add both DAMAGE_PERCENT and DURABILITY_SAVE.
     * 
     * @param weapon The weapon
     * @param weaponLevel The weapon's current level
     * @return New ItemStack with updated effects
     */
    @Nonnull
    public ItemStack updateAllStandardEffects(
            @Nonnull final ItemStack weapon,
            final int weaponLevel
    ) {
        ItemStack updated = this.updateDamagePercentEffect(weapon, weaponLevel);
        updated = this.updateDurabilitySaveEffect(updated, weaponLevel);
        return updated;
    }
    
    // ==================== Effect Application ====================
    
    /**
     * Apply all on-damage effects for a weapon.
     * Called from ItemExpDamageSystem when damage is dealt.
     * Only applies effects that match the weapon's category.
     * 
     * @param context The effect context with damage info
     */
    public void applyOnDamageEffects(@Nonnull final EffectContext context) {
        final List<WeaponEffectInstance> effects = this.getEffects(context.getWeapon());
        final WeaponCategory category = context.getWeaponCategory();
        
        for (final WeaponEffectInstance effect : effects) {
            final WeaponEffectType type = effect.getType();
            if (type == null) {
                continue;
            }
            
            // Check if this effect applies to the weapon's category
            if (!type.appliesTo(category)) {
                // Effect doesn't apply to this weapon type, skip it
                continue;
            }
            
            final EffectProcessor processor = this.processors.get(type);
            final WeaponEffectDefinition definition = this.definitions.get(type);
            
            if (processor == null || definition == null) {
                System.out.println("[WeaponEffect] Warning: No processor/definition for " + type);
                continue;
            }
            
            // Apply the effect
            try {
                processor.onDamageDealt(context, effect, definition);
            } catch (final Exception e) {
                System.err.println("[WeaponEffect] Error applying effect " + type + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Calculate the total damage bonus multiplier from all effects.
     * Useful for displaying stats to the player.
     */
    public double calculateTotalDamageMultiplier(@Nullable final ItemStack weapon) {
        double multiplier = 1.0;
        
        final WeaponEffectInstance percentEffect = this.getEffect(weapon, WeaponEffectType.DAMAGE_PERCENT);
        if (percentEffect != null) {
            final WeaponEffectDefinition def = this.definitions.get(WeaponEffectType.DAMAGE_PERCENT);
            if (def != null) {
                multiplier += def.calculateValue(percentEffect.getLevel());
            }
        }
        
        return multiplier;
    }
    
    /**
     * Get a summary string of all effects on a weapon.
     */
    @Nonnull
    public String getEffectsSummary(@Nullable final ItemStack weapon) {
        final List<WeaponEffectInstance> effects = this.getEffects(weapon);
        
        if (effects.isEmpty()) {
            return "No effects";
        }
        
        final StringBuilder sb = new StringBuilder();
        for (final WeaponEffectInstance effect : effects) {
            final WeaponEffectDefinition def = this.definitions.get(effect.getType());
            if (def != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(def.getFormattedDescription(effect.getLevel()));
            }
        }
        return sb.toString();
    }
}
