package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.tokebak.EchoesOfOrbis.inventory.ItemTagUtil;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Applies stat modifiers from bauble ring effects (RING_STAMINA, RING_HEALTH).
 * Attack power from rings is applied in PlayerAttackPowerDamageSystem.
 */
public final class PlayerStatModifierService {

    private static final String STAMINA_MODIFIER_KEY = "EooStaminaRings";
    private static final String HEALTH_MODIFIER_KEY = "EooHealthRings";
    private static final String HEALTH_REGEN_MODIFIER_KEY = "EooHealthRegenRings";
    private static final String RESIST_MAGIC_MODIFIER_KEY = "EooResistMagicRings";
    private static final String RESIST_PROJECTILE_MODIFIER_KEY = "EooResistProjectileArmor";
    private static final String RESIST_PHYSICAL_MODIFIER_KEY = "EooResistPhysicalArmor";
    private static final String RESIST_FIRE_MODIFIER_KEY = "EooResistFireArmor";
    private static final String RESIST_GENERAL_MODIFIER_KEY = "EooResistGeneralArmor";

    private PlayerStatModifierService() {}

    /**
     * Sum effect value for a given type across all Bauble_Ring stacks in the container.
     */
    private static double sumEffectValue(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService,
            @Nonnull WeaponEffectType effectType
    ) {
        if (baubleContainer == null) return 0.0;
        WeaponEffectDefinition def = effectsService.getDefinition(effectType);
        if (def == null) return 0.0;
        double total = 0.0;
        short capacity = baubleContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = baubleContainer.getItemStack(i);
            if (stack == null || ItemStack.isEmpty(stack) || !ItemTagUtil.hasTag(stack, "Bauble_Ring")) continue;
            List<WeaponEffectInstance> effects = effectsService.getEffects(stack);
            for (WeaponEffectInstance inst : effects) {
                if (inst != null && inst.getType() == effectType) {
                    total += def.calculateValue(inst.getLevel());
                }
            }
        }
        return total;
    }

    /**
     * Sum effect value for a given type across all armor pieces in the container.
     * Only counts items that are armor (item.getArmor() != null).
     */
    private static double sumEffectValueFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService,
            @Nonnull WeaponEffectType effectType
    ) {
        if (armorContainer == null) return 0.0;
        WeaponEffectDefinition def = effectsService.getDefinition(effectType);
        if (def == null) return 0.0;
        double total = 0.0;
        short capacity = armorContainer.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = armorContainer.getItemStack(i);
            if (stack == null || ItemStack.isEmpty(stack) || stack.getItem() == null || stack.getItem().getArmor() == null) continue;
            List<WeaponEffectInstance> effects = effectsService.getEffects(stack);
            for (WeaponEffectInstance inst : effects) {
                if (inst != null && inst.getType() == effectType) {
                    total += def.calculateValue(inst.getLevel());
                }
            }
        }
        return total;
    }

    /**
     * Total stamina bonus from all ring effects in the bauble container.
     */
    public static double getStaminaBonusFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_STAMINA);
    }

    /**
     * Total health bonus from all ring effects in the bauble container.
     */
    public static double getHealthBonusFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_HEALTH);
    }

    /**
     * Damage multiplier from ring attack power effects (1.0 + sum of RING_ATTACK_POWER values).
     */
    public static float getAttackPowerMultiplierFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        double sum = sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_ATTACK_POWER);
        return (float) (1.0 + sum);
    }

    /**
     * Total health regen bonus from rings (same idea as food Health Regen I). Applied as modifier if engine has RegenHealth stat.
     */
    public static double getHealthRegenBonusFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_HEALTH_REGEN);
    }

    /**
     * Total resist magic from rings (fraction, e.g. 0.25 = 25%). Applied as modifier if engine has ResistMagic stat.
     */
    public static double getResistMagicFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_RESIST_MAGIC);
    }

    /**
     * Total thorns damage from rings (reflected when player is hit). Used by ThornsDamageSystem.
     */
    public static double getThornsFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_THORNS);
    }

    /**
     * Total signature energy bonus from rings (+N per attack). Used by ItemExpDamageSystem.
     */
    public static double getSignatureEnergyBonusFromRings(
            @Nullable ItemContainer baubleContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValue(baubleContainer, effectsService, WeaponEffectType.RING_SIGNATURE_ENERGY);
    }

    // ==================== Armor container (ring-and-armor effects + armor-only resistance) ====================

    public static double getStaminaBonusFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.RING_STAMINA);
    }

    public static double getHealthBonusFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.RING_HEALTH);
    }

    public static double getResistMagicFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.RING_RESIST_MAGIC);
    }

    public static double getThornsFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.RING_THORNS);
    }

    public static double getSignatureEnergyBonusFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.RING_SIGNATURE_ENERGY);
    }

    public static double getProjectileResistanceFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.ARMOR_PROJECTILE_RESISTANCE);
    }

    public static double getPhysicalResistanceFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.ARMOR_PHYSICAL_RESISTANCE);
    }

    public static double getFireResistanceFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.ARMOR_FIRE_RESISTANCE);
    }

    public static double getGeneralResistanceFromArmor(
            @Nullable ItemContainer armorContainer,
            @Nonnull WeaponEffectsService effectsService
    ) {
        return sumEffectValueFromArmor(armorContainer, effectsService, WeaponEffectType.ARMOR_GENERAL_RESISTANCE);
    }

    /**
     * Applies or removes the stamina modifier from ring effects.
     * Caller should then set StatModifiersManager.setRecalculate(true).
     */
    public static void updateStaminaFromRings(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;

        int staminaIndex = DefaultEntityStatTypes.getStamina();
        if (staminaIndex == Integer.MIN_VALUE) return;

        if (bonus > 0) {
            StaticModifier staminaModifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    (float) bonus
            );
            statMap.putModifier(staminaIndex, STAMINA_MODIFIER_KEY, staminaModifier);
        } else {
            statMap.removeModifier(staminaIndex, STAMINA_MODIFIER_KEY);
        }
    }

    /**
     * Applies or removes the health modifier from ring effects.
     * Caller should then set StatModifiersManager.setRecalculate(true).
     */
    public static void updateHealthFromRings(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        if (healthIndex == Integer.MIN_VALUE) return;

        if (bonus > 0) {
            StaticModifier healthModifier = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    (float) bonus
            );
            statMap.putModifier(healthIndex, HEALTH_MODIFIER_KEY, healthModifier);
        } else {
            statMap.removeModifier(healthIndex, HEALTH_MODIFIER_KEY);
        }
    }

    /**
     * Applies or removes the health regen modifier from ring effects.
     * Uses engine stat "RegenHealth" if present; no-op otherwise.
     */
    public static void updateHealthRegenFromRings(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;
        int index = EntityStatType.getAssetMap().getIndex("RegenHealth");
        if (index == Integer.MIN_VALUE) return;
        if (bonus > 0) {
            StaticModifier mod = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    (float) bonus
            );
            statMap.putModifier(index, HEALTH_REGEN_MODIFIER_KEY, mod);
        } else {
            statMap.removeModifier(index, HEALTH_REGEN_MODIFIER_KEY);
        }
    }

    /**
     * Applies or removes the resist magic modifier from ring effects.
     * Uses engine stat "ResistMagic" if present; no-op otherwise.
     */
    public static void updateResistMagicFromRings(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        applyResistStat(ref, store, "ResistMagic", RESIST_MAGIC_MODIFIER_KEY, bonus);
    }

    /**
     * Applies or removes projectile resistance from armor. No-op if engine has no such stat.
     */
    public static void updateResistProjectileFromArmor(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        applyResistStat(ref, store, "ResistProjectile", RESIST_PROJECTILE_MODIFIER_KEY, bonus);
    }

    /**
     * Applies or removes physical resistance from armor. No-op if engine has no such stat.
     */
    public static void updateResistPhysicalFromArmor(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        applyResistStat(ref, store, "ResistPhysical", RESIST_PHYSICAL_MODIFIER_KEY, bonus);
    }

    /**
     * Applies or removes fire resistance from armor. No-op if engine has no such stat.
     */
    public static void updateResistFireFromArmor(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        applyResistStat(ref, store, "ResistFire", RESIST_FIRE_MODIFIER_KEY, bonus);
    }

    /**
     * Applies or removes general resistance from armor. No-op if engine has no such stat.
     */
    public static void updateResistGeneralFromArmor(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            double bonus
    ) {
        applyResistStat(ref, store, "ResistGeneral", RESIST_GENERAL_MODIFIER_KEY, bonus);
    }

    private static void applyResistStat(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull String statName,
            @Nonnull String modifierKey,
            double bonus
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return;
        int index = EntityStatType.getAssetMap().getIndex(statName);
        if (index == Integer.MIN_VALUE) return;
        if (bonus > 0) {
            StaticModifier mod = new StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,
                    (float) bonus
            );
            statMap.putModifier(index, modifierKey, mod);
        } else {
            statMap.removeModifier(index, modifierKey);
        }
    }
}
