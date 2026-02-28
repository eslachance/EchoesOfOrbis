package com.tokebak.EchoesOfOrbis.services.effects;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.tokebak.EchoesOfOrbis.services.WeaponMaterialService;
import com.tokebak.EchoesOfOrbis.services.effects.modules.AttackPowerRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ArmorFireResistanceEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ArmorGeneralResistanceEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ToolDropBonusEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ArmorPhysicalResistanceEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ArmorProjectileResistanceEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.DamagePercentEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.HealthRegenRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.DurabilitySaveEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.FireOnHitEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.FreezeOnHitEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.HealthRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.LifeLeechEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.MultishotEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.PoisonOnHitEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ResistMagicRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.SignatureEnergyRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.SlowOnHitEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.StaminaRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.modules.ThornsRingEffectModule;
import com.tokebak.EchoesOfOrbis.services.effects.processors.EffectProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Central service for managing weapon effects.
 *
 * Effect modules register themselves here and provide definition, processor, and display strings.
 * The service is a registry; each EffectModule is self-contained (values, processor, descriptions).
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

    private final Map<WeaponEffectType, WeaponEffectDefinition> definitions;
    private final Map<WeaponEffectType, EffectProcessor> processors;
    private final Map<WeaponEffectType, EffectModule> modules;

    public WeaponEffectsService() {
        this.definitions = new EnumMap<>(WeaponEffectType.class);
        this.processors = new EnumMap<>(WeaponEffectType.class);
        this.modules = new EnumMap<>(WeaponEffectType.class);

        this.registerDefaults();
    }

    /**
     * Register effect modules. Each module is self-contained (definition, processor, display strings).
     */
    private void registerDefaults() {
        this.register(new DamagePercentEffectModule());
        this.register(new LifeLeechEffectModule());
        this.register(new DurabilitySaveEffectModule());
        this.register(new PoisonOnHitEffectModule());
        this.register(new FireOnHitEffectModule());
        this.register(new SlowOnHitEffectModule());
        this.register(new FreezeOnHitEffectModule());
        this.register(new MultishotEffectModule());
        this.register(new StaminaRingEffectModule());
        this.register(new HealthRingEffectModule());
        this.register(new AttackPowerRingEffectModule());
        this.register(new HealthRegenRingEffectModule());
        this.register(new ResistMagicRingEffectModule());
        this.register(new ThornsRingEffectModule());
        this.register(new SignatureEnergyRingEffectModule());
        this.register(new ArmorProjectileResistanceEffectModule());
        this.register(new ArmorPhysicalResistanceEffectModule());
        this.register(new ArmorFireResistanceEffectModule());
        this.register(new ArmorGeneralResistanceEffectModule());
        this.register(new ToolDropBonusEffectModule());
    }

    /**
     * Register an effect module. The module provides definition, processor, and short description.
     * Call this to add new effects or from mods.
     */
    public void register(@Nonnull final EffectModule module) {
        final WeaponEffectType type = module.getType();
        this.definitions.put(type, module.getDefinition());
        this.processors.put(type, module.getProcessor());
        this.modules.put(type, module);
    }

    /**
     * Register an effect definition (for backward compatibility or when not using a full module).
     */
    public void registerDefinition(@Nonnull final WeaponEffectDefinition definition) {
        this.definitions.put(definition.getType(), definition);
    }

    /**
     * Register an effect processor (for backward compatibility or when not using a full module).
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

    /**
     * Get the short description for UI (e.g. "Bonus damage as percentage of hit").
     * Returns null if no module is registered for this effect type.
     */
    @Nullable
    public String getShortDescription(@Nonnull final WeaponEffectType type) {
        final EffectModule module = this.modules.get(type);
        return module == null ? null : module.getShortDescription();
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
     * Scans metadata directly to avoid allocating a full list.
     */
    @Nullable
    public WeaponEffectInstance getEffect(
            @Nullable final ItemStack weapon,
            @Nonnull final WeaponEffectType type
    ) {
        if (weapon == null || weapon.isEmpty()) return null;
        final WeaponEffectInstance[] effects = (WeaponEffectInstance[]) weapon.getFromMetadataOrNull(
                META_KEY_EFFECTS, EFFECTS_CODEC);
        if (effects == null) return null;
        for (final WeaponEffectInstance effect : effects) {
            if (effect != null && effect.getType() == type) return effect;
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
     * Remove an effect from a weapon.
     * Returns a new ItemStack with the effect removed.
     * 
     * @param weapon The weapon to modify
     * @param type The effect type to remove
     * @return New ItemStack with updated metadata
     */
    @Nonnull
    public ItemStack removeEffect(
            @Nonnull final ItemStack weapon,
            @Nonnull final WeaponEffectType type
    ) {
        final List<WeaponEffectInstance> effects = this.getEffects(weapon);
        
        // Remove the effect if it exists
        effects.removeIf(effect -> effect.getType() == type);
        
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
     * Generic method to update any effect type for a given weapon level.
     * Used by the embue system when effects are unlocked and need to scale with level.
     * 
     * @param weapon The weapon
     * @param effectType The type of effect to update
     * @param weaponLevel The weapon's current level
     * @return New ItemStack with updated effect
     */
    @Nonnull
    public ItemStack updateEffectForLevel(
            @Nonnull final ItemStack weapon,
            @Nonnull final WeaponEffectType effectType,
            final int weaponLevel
    ) {
        // Effect level = weaponLevel - 1 (level 1 weapons have no bonus)
        final int effectLevel = weaponLevel - 1;
        
        // Don't add effect if level would be 0 or negative
        if (effectLevel < 1) {
            return weapon;
        }
        
        final WeaponEffectInstance effect = new WeaponEffectInstance(effectType, effectLevel);
        return this.setEffect(weapon, effect);
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
    
    // ==================== Embue Selection ====================
    
    /**
     * Get a list of effect types that can be selected as new effects for a weapon.
     * Filters by:
     * - Weapon category (only effects that apply to this weapon type)
     * - Already unlocked effects (excludes them)
     * - DAMAGE_PERCENT is now selectable (no longer automatic)
     * - Has a registered definition (effect is implemented)
     *
     * @param category The weapon's category
     * @param alreadyUnlocked List of already unlocked effect type IDs
     * @return List of selectable effect types
     */
    @Nonnull
    public List<WeaponEffectType> getSelectableEffects(
            @Nonnull final WeaponCategory category,
            @Nonnull final List<String> alreadyUnlocked
    ) {
        final List<WeaponEffectType> selectable = new ArrayList<>();

        for (final WeaponEffectType type : WeaponEffectType.values()) {
            // Skip FREEZE_ON_HIT - disabled (effect not working on mobs)
            if (type == WeaponEffectType.FREEZE_ON_HIT) {
                continue;
            }

            // Skip if already unlocked
            if (alreadyUnlocked.contains(type.getId())) {
                continue;
            }

            // Skip if doesn't apply to this weapon category
            if (!type.appliesTo(category)) {
                continue;
            }

            // Skip if we don't have a definition for it (not implemented)
            if (!this.definitions.containsKey(type)) {
                continue;
            }

            selectable.add(type);
        }

        return selectable;
    }

    /**
     * Get random upgrade options for the Vampire Survivors-style selection.
     * Each option is either a boost to an existing effect or adding a new effect type.
     *
     * @param weapon The weapon
     * @param category The weapon's category
     * @param count Maximum number of options to return (typically 3)
     * @return List of random upgrade options
     */
    @Nonnull
    public List<UpgradeOption> getRandomUpgradeOptions(
            @Nonnull final ItemStack weapon,
            @Nonnull final WeaponCategory category,
            final int count
    ) {
        final List<UpgradeOption> pool = new ArrayList<>();
        final List<WeaponEffectInstance> effects = this.getEffects(weapon);
        final List<String> alreadyUnlocked = new ArrayList<>();
        for (final WeaponEffectInstance e : effects) {
            if (e.getType() != null) {
                alreadyUnlocked.add(e.getType().getId());
            }
        }
        final int boostSlots = WeaponMaterialService.getBoostSlotsForWeapon(weapon);
        final boolean canAddNew = effects.size() < boostSlots;

        // Add boost options for existing effects
        for (final WeaponEffectInstance effect : effects) {
            final WeaponEffectType type = effect.getType();
            if (type == null || !type.appliesTo(category)) {
                continue;
            }
            final WeaponEffectDefinition def = this.definitions.get(type);
            if (def == null) {
                continue;
            }
            final int level = effect.getLevel();
            // Only health regen has a cap (T1/T2/T3); no other effects are capped
            if (type == WeaponEffectType.RING_HEALTH_REGEN && level >= 3) {
                continue;
            }
            pool.add(new UpgradeOption.BoostOption(type, level));
        }

        // Add new effect options if under slot limit
        if (canAddNew) {
            final List<WeaponEffectType> selectableNew = this.getSelectableEffects(category, alreadyUnlocked);
            for (final WeaponEffectType type : selectableNew) {
                pool.add(new UpgradeOption.NewEffectOption(type));
            }
        }

        if (pool.size() <= count) {
            Collections.shuffle(pool);
            return pool;
        }
        Collections.shuffle(pool);
        return pool.subList(0, count);
    }
    
    /**
     * Get random selectable effects for embue selection UI.
     * Returns up to `count` random effects that can be selected.
     * 
     * @param category The weapon's category
     * @param alreadyUnlocked List of already unlocked effect type IDs
     * @param count Maximum number of effects to return
     * @return List of random selectable effect types
     */
    @Nonnull
    public List<WeaponEffectType> getRandomSelectableEffects(
            @Nonnull final WeaponCategory category,
            @Nonnull final List<String> alreadyUnlocked,
            final int count
    ) {
        final List<WeaponEffectType> selectable = this.getSelectableEffects(category, alreadyUnlocked);
        
        if (selectable.size() <= count) {
            return selectable;
        }
        
        // Shuffle and take first N
        Collections.shuffle(selectable);
        return selectable.subList(0, count);
    }
    
    // ==================== Display ====================
    
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
                // Health regen is the only effect with a cap (T3); clamp display level and show (MAX)
                final int displayLevel = effect.getType() == WeaponEffectType.RING_HEALTH_REGEN
                        ? Math.min(3, effect.getLevel())
                        : effect.getLevel();
                sb.append(def.getFormattedDescription(displayLevel));
                if (effect.getType() == WeaponEffectType.RING_HEALTH_REGEN && effect.getLevel() >= 3) {
                    sb.append(" (MAX)");
                }
            }
        }
        return sb.toString();
    }
}
