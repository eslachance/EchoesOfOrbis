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
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.PlayerStatModifierService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Applies attack power multiplier from ring effects (RING_ATTACK_POWER) to damage dealt by players.
 * Multiplier = 1.0 + sum of RING_ATTACK_POWER values from bauble rings.
 * Runs after filter damage group and before ApplyDamage.
 */
public final class PlayerAttackPowerDamageSystem extends DamageEventSystem {

    private final BaubleContainerService baubleContainerService;
    private final WeaponEffectsService effectsService;

    public PlayerAttackPowerDamageSystem(
            @Nonnull BaubleContainerService baubleContainerService,
            @Nonnull WeaponEffectsService effectsService
    ) {
        this.baubleContainerService = baubleContainerService;
        this.effectsService = effectsService;
    }

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
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) return;
        var bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
        float multiplier = PlayerStatModifierService.getAttackPowerMultiplierFromRings(bauble, this.effectsService);
        float newAmount = damage.getAmount() * multiplier;
        damage.setAmount(newAmount);
    }
}
