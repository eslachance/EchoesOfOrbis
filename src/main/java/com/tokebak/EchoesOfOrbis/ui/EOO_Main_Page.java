package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class EOO_Main_Page extends InteractiveCustomUIPage<EOO_Main_Page.Data> {

    @Nonnull
    private final ItemExpService itemExpService;

    public EOO_Main_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull ItemExpService itemExpService
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.itemExpService = itemExpService;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder uiCommandBuilder,
            @Nonnull UIEventBuilder uiEventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file
        uiCommandBuilder.append("EOO_Main_Page.ui");

        // Get the player's inventory
        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            uiCommandBuilder.set("#ItemCount.Text", "Error: No player");
            return;
        }

        final Inventory inventory = playerComponent.getInventory();

        // Collect all weapons from hotbar and backpack
        final List<WeaponInfo> weapons = new ArrayList<>();
        collectWeapons(inventory.getHotbar(), "Hotbar", weapons);
        collectWeapons(inventory.getBackpack(), "Backpack", weapons);

        // Update item count
        uiCommandBuilder.set("#ItemCount.Text", weapons.size() + " items found");

        // Add an entry for each weapon
        for (int i = 0; i < weapons.size(); i++) {
            final WeaponInfo weapon = weapons.get(i);

            // Append the entry template
            uiCommandBuilder.append("#ItemList", "EOO_Item_Entry.ui");

            // Set values using selector
            final String sel = "#ItemList[" + i + "]";
            uiCommandBuilder.set(sel + " #ItemName.Text", weapon.name + " [Lv. " + weapon.level + "]");
            uiCommandBuilder.set(sel + " #XpInfo.Text", weapon.xpText);
            uiCommandBuilder.set(sel + " #Effects.Text", weapon.effectsText);
            uiCommandBuilder.set(sel + " #Location.Text", weapon.locationText);
        }
    }

    private void collectWeapons(
            @Nonnull ItemContainer container,
            @Nonnull String containerName,
            @Nonnull List<WeaponInfo> weapons
    ) {
        final short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty() && this.itemExpService.canGainXp(item)) {
                weapons.add(new WeaponInfo(item, containerName, slot));
            }
        }
    }

    private String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        StringBuilder sb = new StringBuilder();
        for (String word : raw.replace("_", " ").split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    // Helper class to hold weapon info
    private class WeaponInfo {
        final String name;
        final int level;
        final String xpText;
        final String effectsText;
        final String locationText;

        WeaponInfo(ItemStack item, String containerName, int slot) {
            this.name = formatName(item.getItemId());
            this.level = itemExpService.getItemLevel(item);

            final double totalXp = itemExpService.getItemXp(item);
            final double xpForCurrent = itemExpService.getXpRequiredForLevel(level);
            final double xpForNext = itemExpService.getXpRequiredForLevel(level + 1);
            final double currentXp = totalXp - xpForCurrent;
            final double xpNeeded = xpForNext - xpForCurrent;
            final double percent = xpNeeded > 0 ? (currentXp / xpNeeded) * 100 : 0;

            this.xpText = String.format("XP: %.0f / %.0f (%.0f%%)", currentXp, xpNeeded, percent);

            final String effects = itemExpService.getEffectsService().getEffectsSummary(item);
            this.effectsText = effects.isEmpty() ? "Effects: None" : "Effects: " + effects;

            this.locationText = "Location: " + containerName + " slot " + (slot + 1);
        }
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        super.handleDataEvent(ref, store, data);
        sendUpdate();
    }

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .build();
    }
}
