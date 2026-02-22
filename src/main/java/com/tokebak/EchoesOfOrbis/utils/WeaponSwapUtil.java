package com.tokebak.EchoesOfOrbis.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utility for swapping weapons while preserving or maximizing SignatureEnergy.
 * When items are swapped in the hotbar, the game resets SignatureEnergy to 0.
 * This helper captures the current value before the swap, then restores or
 * maximizes it after a delay (like SignaturePreservation mod) so the game's
 * internal reset has completed first.
 */
public final class WeaponSwapUtil {

    private static final int SIGNATURE_ENERGY_INDEX = EntityStatType.getAssetMap().getIndex("SignatureEnergy");

    /**
     * Delay before restoring SignatureEnergy, matching SignaturePreservation mod.
     * The game's reset happens asynchronously; we must wait for it to complete.
     */
    private static final long RESTORE_DELAY_MS = 100L;

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "EOO-SignatureEnergy-Restore");
        t.setDaemon(true);
        return t;
    });

    private WeaponSwapUtil() {}

    /**
     * Swap a weapon in the hotbar while preserving SignatureEnergy.
     * Saves the current value before swap, restores it after.
     */
    public static void swapWeaponPreservingSignature(
            @Nonnull final Ref<EntityStore> playerRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final int slot,
            @Nonnull final ItemStack newWeapon
    ) {
        swapWeapon(playerRef, store, inventory, slot, newWeapon, false);
    }

    /**
     * Swap a weapon in the hotbar and maximize SignatureEnergy after the swap.
     * Use when applying level-up bonus (e.g. after upgrade selection).
     */
    public static void swapWeaponAndMaximizeSignature(
            @Nonnull final Ref<EntityStore> playerRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final int slot,
            @Nonnull final ItemStack newWeapon
    ) {
        swapWeapon(playerRef, store, inventory, slot, newWeapon, true);
    }

    /**
     * Swap a weapon in the given container.
     * For Hotbar: preserves or maximizes SignatureEnergy.
     * For other containers: direct swap (no SignatureEnergy handling).
     */
    public static void swapWeaponInContainer(
            @Nonnull final Ref<EntityStore> playerRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            @Nonnull final String containerName,
            final int slot,
            @Nonnull final ItemStack newWeapon,
            final boolean maximizeSignatureAfter
    ) {
        if ("Hotbar".equals(containerName)) {
            swapWeapon(playerRef, store, inventory, slot, newWeapon, maximizeSignatureAfter);
        } else {
            final ItemContainer container = getContainer(inventory, containerName);
            if (container != null) {
                container.setItemStackForSlot((short) slot, newWeapon);
            }
        }
    }

    @Nullable
    private static ItemContainer getContainer(@Nonnull final Inventory inventory, @Nonnull final String containerName) {
        return switch (containerName) {
            case "Hotbar" -> inventory.getHotbar();
            case "Storage" -> inventory.getStorage();
            case "Backpack" -> inventory.getBackpack();
            case "Armor" -> inventory.getArmor();
            default -> null;
        };
    }

    private static void swapWeapon(
            @Nonnull final Ref<EntityStore> playerRef,
            @Nonnull final Store<EntityStore> store,
            @Nonnull final Inventory inventory,
            final int slot,
            @Nonnull final ItemStack newWeapon,
            final boolean maximizeAfter
    ) {
        final float savedEnergy = maximizeAfter ? -1f : getSignatureEnergy(playerRef, store);

        final ItemContainer hotbar = inventory.getHotbar();
        hotbar.setItemStackForSlot((short) slot, newWeapon);

        if (maximizeAfter || savedEnergy >= 0) {
            final World world = ((EntityStore) store.getExternalData()).getWorld();
            final boolean doMaximize = maximizeAfter;
            final float energyToRestore = savedEnergy;

            // Schedule restore with delay so the game's reset completes first (like SignaturePreservation)
            scheduler.schedule(() -> {
                world.execute(() -> {
                    if (!playerRef.isValid()) return;
                    if (doMaximize) {
                        maximizeSignatureEnergy(playerRef, store);
                    } else if (energyToRestore >= 0) {
                        setSignatureEnergy(playerRef, store, energyToRestore);
                    }
                });
            }, RESTORE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    @SuppressWarnings("unchecked")
    private static float getSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        if (SIGNATURE_ENERGY_INDEX == Integer.MIN_VALUE) return -1f;
        final EntityStatMap statMap = (EntityStatMap) store.getComponent((Ref) entityRef, EntityStatMap.getComponentType());
        if (statMap == null) return -1f;
        final var statValue = statMap.get(SIGNATURE_ENERGY_INDEX);
        return statValue != null ? statValue.get() : -1f;
    }

    @SuppressWarnings("unchecked")
    private static void setSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            final float value
    ) {
        if (SIGNATURE_ENERGY_INDEX == Integer.MIN_VALUE) return;
        final EntityStatMap statMap = (EntityStatMap) store.getComponent((Ref) entityRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            statMap.setStatValue(SIGNATURE_ENERGY_INDEX, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void maximizeSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store
    ) {
        if (SIGNATURE_ENERGY_INDEX == Integer.MIN_VALUE) return;
        final EntityStatMap statMap = (EntityStatMap) store.getComponent((Ref) entityRef, EntityStatMap.getComponentType());
        if (statMap != null) {
            statMap.maximizeStatValue(SIGNATURE_ENERGY_INDEX);
        }
    }

    /**
     * Add bonus signature energy to the entity (e.g. from RING_SIGNATURE_ENERGY). Engine caps at max.
     */
    public static void addSignatureEnergy(
            @Nonnull final Ref<EntityStore> entityRef,
            @Nonnull final Store<EntityStore> store,
            final float bonus
    ) {
        if (SIGNATURE_ENERGY_INDEX == Integer.MIN_VALUE || bonus <= 0) return;
        float current = getSignatureEnergy(entityRef, store);
        if (current < 0) return;
        setSignatureEnergy(entityRef, store, current + bonus);
    }
}
