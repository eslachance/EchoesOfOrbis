package com.tokebak.EchoesOfOrbis.services;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies our mod's EOO_Healing_Effect to players who have RING_HEALTH_REGEN in their bauble.
 * Prefers tiered T1/T2/T3 effects when found; falls back to single EOO_Healing_Effect (same as before renames).
 */
public final class RingHealthRegenEffectApplier {

    private static final String[] EOO_HEALING_EFFECT_TIER_IDS = {
            "EOO_Healing_Effect_T1",
            "EOO_Healing_Effect_T2",
            "EOO_Healing_Effect_T3",
    };

    /** Fallback: single effect that worked before tiered rename (Server/.../EOO_Healing_Effect.json). */
    private static final String EOO_HEALING_EFFECT_ID = "EOO_Healing_Effect";

    private static final EntityEffect[] cachedTierEffects = new EntityEffect[3];
    private static EntityEffect cachedSingleEffect;
    private static boolean singleEffectLookupAttempted;

    private RingHealthRegenEffectApplier() {}

    /**
     * Apply EOO_Healing_Effect (tiered if found, else single) to the player if they have RING_HEALTH_REGEN.
     */
    public static void applyIfHasRing(
            @Nonnull final Ref<EntityStore> ref,
            @Nonnull final Store<EntityStore> store,
            @Nullable final ItemContainer bauble,
            @Nonnull final WeaponEffectsService effectsService
    ) {
        if (bauble == null) return;
        double bonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, effectsService);
        if (bonus <= 0) return;

        int tierIndex = tierFromBonus(bonus);
        EntityEffect effect = getEffectForTier(tierIndex);
        if (effect == null) {
            effect = getEooHealingEffectFallback();
        }
        if (effect == null) return;

        EffectControllerComponent effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectController == null) return;

        effectController.addEffect(ref, effect, store);
    }

    /**
     * Map ring regen bonus to tier index 0 (T1), 1 (T2), 2 (T3).
     */
    public static int tierFromBonus(final double bonus) {
        if (bonus <= 0) return -1;
        if (bonus <= 1.0) return 0;
        if (bonus <= 1.5) return 1;
        return 2;
    }

    /**
     * Get the EntityEffect for tier (0=T1, 1=T2, 2=T3). Tries several ID variants; cached per tier.
     */
    @Nullable
    public static EntityEffect getEffectForTier(final int tierIndex) {
        if (tierIndex < 0 || tierIndex > 2) return null;
        EntityEffect cached = cachedTierEffects[tierIndex];
        if (cached != null) return cached;
        String baseId = EOO_HEALING_EFFECT_TIER_IDS[tierIndex];
        for (String variant : new String[] {
                baseId,
                "Deployables/" + baseId,
                "Entity/Effects/Deployables/" + baseId,
        }) {
            int index = EntityEffect.getAssetMap().getIndex(variant);
            if (index != Integer.MIN_VALUE) {
                cached = EntityEffect.getAssetMap().getAsset(index);
                cachedTierEffects[tierIndex] = cached;
                return cached;
            }
        }
        return null;
    }

    /**
     * Fallback single effect (EOO_Healing_Effect) so regen works even if tiered IDs are wrong.
     */
    @Nullable
    public static EntityEffect getEooHealingEffectFallback() {
        if (cachedSingleEffect != null) return cachedSingleEffect;
        if (singleEffectLookupAttempted) return null;
        singleEffectLookupAttempted = true;
        for (String variant : new String[] { EOO_HEALING_EFFECT_ID, "Deployables/" + EOO_HEALING_EFFECT_ID }) {
            int index = EntityEffect.getAssetMap().getIndex(variant);
            if (index != Integer.MIN_VALUE) {
                cachedSingleEffect = EntityEffect.getAssetMap().getAsset(index);
                return cachedSingleEffect;
            }
        }
        return null;
    }
}
