package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EOO_Main_Page extends InteractiveCustomUIPage<EOO_Main_Page.Data> {

    @Nonnull
    private final ItemExpService itemExpService;
    
    @Nonnull
    private final PlayerRef playerRef;
    
    // Store weapon info for click event handling
    private List<WeaponInfo> weaponsList;

    public EOO_Main_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull ItemExpService itemExpService
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.playerRef = playerRef;
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

        // Collect all weapons from hotbar, storage (main inventory), and backpack
        this.weaponsList = new ArrayList<>();
        collectWeapons(inventory.getHotbar(), "Hotbar", this.weaponsList);
        collectWeapons(inventory.getStorage(), "Storage", this.weaponsList);
        collectWeapons(inventory.getBackpack(), "Backpack", this.weaponsList);

        // Sort: used items first (by level desc, then XP desc), unused items at bottom
        this.weaponsList.sort(Comparator
                .comparing((WeaponInfo w) -> w.isUnused)           // unused items last
                .thenComparing((WeaponInfo w) -> w.level, Comparator.reverseOrder())  // highest level first
                .thenComparing((WeaponInfo w) -> w.totalXp, Comparator.reverseOrder()) // highest XP first
        );

        // Update item count
        uiCommandBuilder.set("#ItemCount.Text", this.weaponsList.size() + " items found");

        // Add an entry for each weapon
        for (int i = 0; i < this.weaponsList.size(); i++) {
            final WeaponInfo weapon = this.weaponsList.get(i);

            // Append the entry template
            uiCommandBuilder.append("#ItemList", "EOO_Item_Entry.ui");

            // Set values using selector
            final String sel = "#ItemList[" + i + "]";

            if (weapon.isUnused) {
                // Unused items: grey text, no XP/effects shown
                uiCommandBuilder.set(sel + " #ItemName.Text", buildUnusedItemLabel(weapon));
                uiCommandBuilder.set(sel + " #ItemName.Style.TextColor", "#666666");
                uiCommandBuilder.set(sel + " #XpInfo.Visible", false);
                uiCommandBuilder.set(sel + " #Effects.Visible", false);
                uiCommandBuilder.set(sel + " #Location.Text", weapon.locationText);
            } else {
                // Active items: full display with category and translated name
                uiCommandBuilder.set(sel + " #ItemName.Text", buildItemLabel(weapon));
                uiCommandBuilder.set(sel + " #XpInfo.Text", weapon.xpText);
                uiCommandBuilder.set(sel + " #Effects.Text", weapon.effectsText);
                uiCommandBuilder.set(sel + " #Location.Text", weapon.locationText);
                
                // Show embue button if there are pending embues
                if (weapon.pendingEmbues > 0) {
                    uiCommandBuilder.set(sel + " #EmbueButton.Visible", true);
                    uiCommandBuilder.set(sel + " #EmbueButton.Text", "+" + weapon.pendingEmbues + " Embues");
                    
                    // Register click event for the embue button
                    uiEventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            sel + " #EmbueButton",
                            EventData.of("Action", "embue").append("WeaponIndex", String.valueOf(i))
                    );
                }
            }
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

    /**
     * Get display name for an item. Uses translation if available, falls back to formatName.
     */
    private String getDisplayName(ItemStack item) {
        final String translationKey = item.getItem().getTranslationKey();
        
        // Try to get the translated name via Message
        final String translated = Message.translation(translationKey).getAnsiMessage();
        
        // If translation succeeded (not empty and not just the key), use it
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
            return translated;
        }
        
        // Fallback: parse the item ID into a readable name
        final String itemId = item.getItem().getId();
        return formatName(itemId);
    }
    
    /**
     * Build a full item label with name, category, and level info.
     */
    private String buildItemLabel(WeaponInfo weapon) {
        final String name = getDisplayName(weapon.item);
        
        // Build label with category and level (embues shown in separate button)
        return name + " [" + weapon.categoryText + "] [Lv. " + weapon.level + "]";
    }
    
    /**
     * Build label for unused items.
     */
    private String buildUnusedItemLabel(WeaponInfo weapon) {
        final String name = getDisplayName(weapon.item);
        return name + " [" + weapon.categoryText + "] [unused]";
    }
    
    /**
     * Fallback name formatter - parses item ID into readable name.
     * E.g., "Weapon_Sword_Crude" -> "Crude Sword"
     */
    private String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        
        // Remove common prefixes
        String cleaned = raw;
        if (cleaned.startsWith("Weapon_")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("Tool_")) cleaned = cleaned.substring(5);
        
        // Split by underscore and capitalize each word
        StringBuilder sb = new StringBuilder();
        String[] parts = cleaned.split("_");
        
        // Reverse order often makes more sense (e.g., Sword_Crude -> Crude Sword)
        for (int i = parts.length - 1; i >= 0; i--) {
            String word = parts[i];
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
        final ItemStack item;
        final String containerName;
        final int slot;
        final int level;
        final double totalXp;
        final WeaponCategory category;
        final String categoryText;
        final String xpText;
        final String effectsText;
        final String locationText;
        final boolean isUnused;
        final int pendingEmbues;

        WeaponInfo(ItemStack item, String containerName, int slot) {
            this.item = item;
            this.containerName = containerName;
            this.slot = slot;
            this.level = itemExpService.getItemLevel(item);
            this.totalXp = itemExpService.getItemXp(item);
            this.isUnused = this.totalXp == 0;
            this.pendingEmbues = itemExpService.getPendingEmbues(item);
            
            // Determine weapon category from item ID (no damage event available here)
            this.category = WeaponCategoryUtil.determineCategory(null, item);
            this.categoryText = WeaponCategoryUtil.getDisplayName(this.category);

            final double xpForCurrent = itemExpService.getXpRequiredForLevel(level);
            final double xpForNext = itemExpService.getXpRequiredForLevel(level + 1);
            final double currentXp = this.totalXp - xpForCurrent;
            final double xpNeeded = xpForNext - xpForCurrent;
            final double percent = xpNeeded > 0 ? (currentXp / xpNeeded) * 100 : 0;

            this.xpText = String.format("XP: %.0f / %.0f (%.0f%%)", currentXp, xpNeeded, percent);

            final String effects = itemExpService.getEffectsService().getEffectsSummary(item);
            this.effectsText = effects.isEmpty() ? "Effects: None" : "Effects: " + effects;

            this.locationText = containerName + " slot " + (slot + 1);
        }
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        // Handle embue button click
        if ("embue".equals(data.action) && data.weaponIndex != null) {
            try {
                final int weaponIdx = Integer.parseInt(data.weaponIndex);
                if (weaponIdx >= 0 && this.weaponsList != null && weaponIdx < this.weaponsList.size()) {
                    final WeaponInfo weapon = this.weaponsList.get(weaponIdx);
                    
                    // Only open selection if there are pending embues
                    if (weapon.pendingEmbues > 0) {
                        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
                        if (playerComponent != null) {
                            // Open the embue selection page
                            final EOO_Embue_Selection_Page selectionPage = new EOO_Embue_Selection_Page(
                                    this.playerRef,
                                    CustomPageLifetime.CanDismiss,
                                    this.itemExpService,
                                    weapon.containerName,
                                    weapon.slot
                            );
                            playerComponent.getPageManager().openCustomPage(ref, store, selectionPage);
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
                // Invalid weapon index
            }
            return;
        }
        
        super.handleDataEvent(ref, store, data);
        sendUpdate();
    }

    public static class Data {
        public String action;
        public String weaponIndex;
        
        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(
                        new KeyedCodec<>("Action", Codec.STRING),
                        (d, v) -> d.action = v,
                        d -> d.action
                ).add()
                .append(
                        new KeyedCodec<>("WeaponIndex", Codec.STRING),
                        (d, v) -> d.weaponIndex = v,
                        d -> d.weaponIndex
                ).add()
                .build();
    }
}
