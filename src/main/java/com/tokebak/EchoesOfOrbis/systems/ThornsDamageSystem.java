package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.PlayerStatModifierService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.services.effects.processors.DamagePercentProcessor;

import javax.annotation.Nonnull;
import java.util.Set;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;

/**
 * When the player (victim) takes damage from an entity, reflect thorns damage back at the attacker.
 * Only runs when the damage target is a player and the source is an entity; uses RING_THORNS from bauble rings.
 */
public final class ThornsDamageSystem extends DamageEventSystem {

    private final BaubleContainerService baubleContainerService;
    private final WeaponEffectsService effectsService;

    public ThornsDamageSystem(
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
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final Damage damage
    ) {
        if (damage.isCancelled() || damage.getAmount() <= 0) return;
        if (DamagePercentProcessor.isBonusDamage(damage)) return;

        final Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        final Player victimPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (victimPlayer == null) return;

        final Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        final Ref<EntityStore> attackerRef = (Ref<EntityStore>) entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;
        if (attackerRef.equals(targetRef)) return;

        var bauble = baubleContainerService.getOrCreate(victimPlayer.getPlayerRef());
        ItemContainer armor = victimPlayer.getInventory() != null ? victimPlayer.getInventory().getArmor() : null;
        double thornsAmount = PlayerStatModifierService.getThornsFromRings(bauble, effectsService)
                + PlayerStatModifierService.getThornsFromArmor(armor, effectsService);
        if (thornsAmount < 0.1) return;

        float thornsDamageAmount = (float) thornsAmount;
        Damage.Source thornsSource = new Damage.EntitySource(targetRef);
        Damage thornsDamage = new Damage(thornsSource, damage.getDamageCauseIndex(), thornsDamageAmount);
        thornsDamage.getMetaStore().putMetaObject(DamagePercentProcessor.IS_BONUS_DAMAGE, Boolean.TRUE);

        commandBuffer.invoke(attackerRef, (EcsEvent) thornsDamage);
    }
}
