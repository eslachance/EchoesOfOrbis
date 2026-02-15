package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Applies a multiplicative bonus to damage dealt by players (general attack power).
 * Same idea as armor "DamageClassEnhancement" (e.g. Armor_Adamantite_Chest +6% light attack)
 * but done programmatically for all damage from players.
 * Runs after filter damage group and before ApplyDamage.
 */
public final class PlayerAttackPowerDamageSystem extends DamageEventSystem {

    /** Multiplicative bonus for player deal damage (1.06 = +6%, same as Adamantite chest light attack). */
    private static final float PLAYER_ATTACK_POWER_MULTIPLIER = 1.5f;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(
                new SystemGroupDependency<EntityStore>(Order.AFTER, DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<EntityStore, DamageSystems.ApplyDamage>(Order.BEFORE, DamageSystems.ApplyDamage.class)
        );
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage
    ) {
        if (damage.getAmount() <= 0f) return;
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource)) return;
        Ref<EntityStore> attackerRef = ((Damage.EntitySource) source).getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;
        if (store.getComponent(attackerRef, Player.getComponentType()) == null) return;
        float newAmount = damage.getAmount() * PLAYER_ATTACK_POWER_MULTIPLIER;
        damage.setAmount(newAmount);
    }
}
