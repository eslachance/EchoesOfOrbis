package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.utils.ItemExpNotifications;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * System that listens for damage events and awards XP to the attacking player's weapon.
 *
 * This extends DamageEventSystem which is called whenever damage is dealt.
 * We check if the attacker is a player, then add XP to their held weapon.
 */
public class ItemExpDamageSystem extends DamageEventSystem {

    private final ItemExpService itemExpService;
    private final EchoesOfOrbisConfig config;

    public ItemExpDamageSystem(

            @Nonnull final ItemExpService itemExpService,
            @Nonnull final EchoesOfOrbisConfig config
    ) {
        super();
        this.itemExpService = itemExpService;
        this.config = config;
    }

    /**
     * Called for every damage event in the game.
     * We filter to only process damage dealt BY players.
     */
    @Override
    public void handle(
            final int index,
            @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final CommandBuffer<EntityStore> commandBuffer,
            @Nonnull final Damage damage
    ) {
        // Skip if damage was cancelled or zero
        if (damage.isCancelled() || damage.getAmount() <= 0) {
            return;
        }

        // Get the damage source - we only care about entity sources (players/mobs)
        final Damage.Source source = damage.getSource();

        // Pattern matching: check if source is an EntitySource
        // This is Java 17+ syntax - instanceof + cast in one step
        if (!(source instanceof final Damage.EntitySource entitySource)) {
            return; // Not entity damage (fall damage, fire, etc.)
        }

        // Get the attacker's entity reference
        final Ref<EntityStore> attackerRef = (Ref<EntityStore>) entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        // Check if attacker is a player (not an NPC/mob)
        final Player attackerPlayer = (Player) store.getComponent(
                (Ref) attackerRef,
                Player.getComponentType()
        );
        if (attackerPlayer == null) {
            return; // Attacker is not a player
        }

        // Get the PlayerRef for notifications and inventory access
        final PlayerRef playerRef = (PlayerRef) store.getComponent(
                (Ref) attackerRef,
                PlayerRef.getComponentType()
        );
        if (playerRef == null) {
            return;
        }

        // Get the player's inventory
        final Inventory inventory = attackerPlayer.getInventory();
        if (inventory == null) {
            return;
        }

        // Get the weapon they're holding
        final ItemStack weapon = inventory.getActiveHotbarItem();
        if (weapon == null || !this.itemExpService.canGainXp(weapon)) {
            return; // No weapon or weapon can't gain XP
        }

        // Calculate XP to award based on damage dealt
        final float damageDealt = damage.getAmount();
        final double xpGained = this.itemExpService.calculateXpFromDamage(damageDealt);

        if (xpGained <= 0) {
            return;
        }

        // Get state before update for level-up detection
        final int levelBefore = this.itemExpService.getItemLevel(weapon);

        // Add XP to the weapon
        final boolean success = this.itemExpService.addXpToHeldWeapon(
                playerRef,
                inventory,
                xpGained
        );

        if (!success) {
            return;
        }

        // Get updated weapon to check new level
        final ItemStack updatedWeapon = inventory.getActiveHotbarItem();
        final int levelAfter = this.itemExpService.getItemLevel(updatedWeapon);

        // Debug: Log XP gain to console
        final double totalXp = this.itemExpService.getItemXp(updatedWeapon);
        final double xpToNextLevel = this.itemExpService.getXpToNextLevel(levelAfter);
        System.out.println(String.format(
                "[ItemExp] %s gained %.2f XP | Total: %.2f | Level: %d | XP to next: %.2f",
                updatedWeapon.getItemId(),
                xpGained,
                totalXp,
                levelAfter,
                xpToNextLevel
        ));

        // Send notifications if enabled
        if (this.config.isShowXpNotifications() && xpGained >= this.config.getMinXpForNotification()) {
            ItemExpNotifications.sendXpGainNotification(playerRef, xpGained, updatedWeapon, this.itemExpService);
        }

        // Check for level up
        if (levelAfter > levelBefore) {
            ItemExpNotifications.sendLevelUpNotification(playerRef, updatedWeapon, levelAfter);
        }
    }

    /**
     * Query that determines which entities this system processes.
     * Query.any() means all entities - we filter inside handle().
     */
    @Nullable
    public Query<EntityStore> getQuery() {
        return Query.any();
    }
}
