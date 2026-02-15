package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Applies mod-specific base stat modifiers for players (e.g. stamina, health, defense).
 * Used as a foundation for baubles/rings that will add more modifiers later.
 */
public final class PlayerStatModifierService {

    private static final String STAMINA_MODIFIER_KEY = "EooStaminaBase";

    /** Target max stamina for all players (vanilla base is 10). */
    private static final float TARGET_STAMINA_MAX = 25f;

    private PlayerStatModifierService() {}

    /**
     * Applies base stat modifiers for this mod (e.g. set stamina max to 25).
     * Safe to call multiple times; re-applying replaces the modifier.
     */
    public static void applyBaseStatModifiers(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store
    ) {
        EntityStatMap statMap = store.getComponent(ref, EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) {
            return;
        }

        int staminaIndex = DefaultEntityStatTypes.getStamina();
        if (staminaIndex == Integer.MIN_VALUE) {
            return;
        }

        EntityStatType staminaAsset = EntityStatType.getAssetMap().getAsset(staminaIndex);
        if (staminaAsset == null) {
            return;
        }

        float additiveAmount = TARGET_STAMINA_MAX - staminaAsset.getMax();
        StaticModifier staminaModifier = new StaticModifier(
                Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,
                additiveAmount
        );
        statMap.putModifier(staminaIndex, STAMINA_MODIFIER_KEY, staminaModifier);
    }
}
