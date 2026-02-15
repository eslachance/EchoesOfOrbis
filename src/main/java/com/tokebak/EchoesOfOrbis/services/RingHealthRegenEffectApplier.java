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
 * Applies our mod's tiered EOO_Healing_Effect (T1/T2/T3) to players who have RING_HEALTH_REGEN in their bauble.
 * T1: +1 Health per tick, T2: +1.5, T3: +2 (1s duration, Overwrite). No screen tint; base totem unchanged.
 */
public final class RingHealthRegenEffectApplier {

    private static final String[] EOO_HEALING_EFFECT_IDS = {
            "EOO_Healing_Effect_T1",
            "EOO_Healing_Effect_T2",
            "EOO_Healing_Effect_T3",
    };

    private static final EntityEffect[] cachedTierEffects = new EntityEffect[3];

    private RingHealthRegenEffectApplier() {}

    /**
     * Apply the appropriate tiered EOO_Healing_Effect (T1/T2/T3) to the player if they have RING_HEALTH_REGEN.
     * Tier is derived from ring level (bonus 1.0 -> T1, 1.5 -> T2, 2.0 -> T3). Call periodically (e.g. every 1s).
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
     * Get the EntityEffect for tier (0=T1, 1=T2, 2=T3). Cached per tier.
     */
    @Nullable
    public static EntityEffect getEffectForTier(final int tierIndex) {
        if (tierIndex < 0 || tierIndex > 2) return null;
        EntityEffect cached = cachedTierEffects[tierIndex];
        if (cached != null) return cached;
        String id = EOO_HEALING_EFFECT_IDS[tierIndex];
        for (String variant : new String[] { id, "Deployables/" + id }) {
            int index = EntityEffect.getAssetMap().getIndex(variant);
            if (index != Integer.MIN_VALUE) {
                cached = EntityEffect.getAssetMap().getAsset(index);
                cachedTierEffects[tierIndex] = cached;
                return cached;
            }
        }
        return null;
    }
}
