package com.tokebak.EchoesOfOrbis.services.effects.processors;

import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.tokebak.EchoesOfOrbis.services.effects.EffectContext;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import javax.annotation.Nonnull;

/**
 * Processor for DAMAGE_PERCENT effect.
 *
 * Applies bonus damage as a second damage event (Filter-phase in-place modification
 * does not work for plugin-registered systems; the engine's damage pipeline may not
 * invoke us in that phase). Bonus damage is marked with IS_BONUS_DAMAGE so we don't
 * recurse (no XP/effects on the bonus hit).
 *
 * Example: If effect value is 0.15 (15%) and original damage is 10,
 * this deals an additional 1.5 damage as a separate hit.
 */
public class DamagePercentProcessor implements EffectProcessor {

    /**
     * Meta key to mark damage events as bonus damage from weapon effects.
     * Prevents infinite recursion - bonus damage won't trigger more bonus damage.
     */
    public static final MetaKey<Boolean> IS_BONUS_DAMAGE = Damage.META_REGISTRY.registerMetaObject(
            data -> Boolean.FALSE
    );

    /**
     * Check if a damage event is bonus damage from weapon effects.
     */
    public static boolean isBonusDamage(@Nonnull final Damage damage) {
        final Boolean isBonusDamage = (Boolean) damage.getMetaStore().getIfPresentMetaObject(IS_BONUS_DAMAGE);
        return isBonusDamage != null && isBonusDamage;
    }

    @Override
    public void onDamageDealt(
            @Nonnull final EffectContext context,
            @Nonnull final WeaponEffectInstance instance,
            @Nonnull final WeaponEffectDefinition definition
    ) {
        // Skip if this damage is from a multishot arrow (prevents double-dipping)
        final int attackerKey = context.getAttackerRef().hashCode();
        if (MultishotProcessor.shouldSkipEffectsForMultishot(attackerKey)) {
            return;
        }

        // Calculate bonus damage percentage based on effect level
        final double percentBonus = definition.calculateValue(instance.getLevel());

        if (percentBonus <= 0) {
            return; // No bonus to apply
        }

        // Calculate bonus damage amount
        final float originalDamage = context.getOriginalDamageAmount();
        final float bonusDamage = (float) (originalDamage * percentBonus);

        if (bonusDamage < 0.1f) {
            return; // Skip tiny damage amounts
        }

        // Create a new damage event for the bonus damage (plugin systems don't run in Filter, so in-place doesn't work)
        final Damage originalDamageEvent = context.getOriginalDamage();
        final Damage bonusDamageEvent = new Damage(
                originalDamageEvent.getSource(),
                originalDamageEvent.getDamageCauseIndex(),
                bonusDamage
        );
        bonusDamageEvent.getMetaStore().putMetaObject(IS_BONUS_DAMAGE, Boolean.TRUE);

        context.getCommandBuffer().invoke(
                context.getTargetRef(),
                (EcsEvent) bonusDamageEvent
        );
    }
}
