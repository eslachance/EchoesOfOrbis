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
 * Applies the same healing EntityEffect used by the Healing Totem deployable (Weapon_Deployable_Healing_Totem).
 * When you stand near the totem it applies "Healing_Totem_Heal" (Duration 1s, +2 Health); we apply that
 * effect periodically to players who have RING_HEALTH_REGEN in their bauble, so the ring acts like a personal totem.
 */
public final class RingHealthRegenEffectApplier {

    /** Effect ID used by the deployable Healing Totem (Entity/Effects/Deployables/Healing_Totem_Heal.json). */
    private static final String HEALING_TOTEM_HEAL_EFFECT_ID = "Healing_Totem_Heal";

    private static EntityEffect cachedHealingTotemEffect;
    private static boolean healingTotemEffectLookupAttempted;

    private RingHealthRegenEffectApplier() {}

    /**
     * Apply the Healing Totem heal effect to the player if they have RING_HEALTH_REGEN in the bauble.
     * Uses the same effect as the deployable: 1s duration, +2 Health (Overwrite). Call periodically (e.g. every 1s).
     *
     * @param ref    Player entity ref
     * @param store  Entity store
     * @param bauble Player's bauble container (can be null)
     * @param effectsService Weapon effects service to sum ring regen
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

        EntityEffect effect = getHealingTotemHealEffect();
        if (effect == null) return;

        EffectControllerComponent effectController = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectController == null) return;

        // Same as deployable: addEffect(targetRef, effectAsset, store) uses effect's built-in duration (1s)
        effectController.addEffect(ref, effect, store);
    }

    /**
     * Get the Healing Totem heal EntityEffect (Healing_Totem_Heal). Tries ID and path variants.
     */
    @Nullable
    public static EntityEffect getHealingTotemHealEffect() {
        if (cachedHealingTotemEffect != null) return cachedHealingTotemEffect;
        if (healingTotemEffectLookupAttempted) return null;
        healingTotemEffectLookupAttempted = true;
        for (String id : new String[] { HEALING_TOTEM_HEAL_EFFECT_ID, "Deployables/Healing_Totem_Heal" }) {
            int index = EntityEffect.getAssetMap().getIndex(id);
            if (index != Integer.MIN_VALUE) {
                cachedHealingTotemEffect = EntityEffect.getAssetMap().getAsset(index);
                return cachedHealingTotemEffect;
            }
        }
        return null;
    }

    /**
     * Map ring regen bonus to tier index 0 (T1), 1 (T2), 2 (T3). Used for display / upgrade tiers only.
     */
    public static int tierFromBonus(final double bonus) {
        if (bonus <= 0) return -1;
        if (bonus <= 1.0) return 0;
        if (bonus <= 1.5) return 1;
        return 2;
    }

    /**
     * Get the EntityEffect asset for tier (0=T1, 1=T2, 2=T3). Used by damage-system path; ring regen now uses Healing_Totem_Heal.
     */
    @Nullable
    public static EntityEffect getEffectForTier(final int tierIndex) {
        return getHealingTotemHealEffect();
    }
}
