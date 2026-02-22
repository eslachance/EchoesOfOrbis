package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
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
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
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
    private final BaubleContainerService baubleContainerService;
    @Nonnull
    private final PlayerRef playerRef;

    // Store weapon info for click event handling
    private List<WeaponInfo> weaponsList;

    public EOO_Main_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull ItemExpService itemExpService,
            @Nonnull BaubleContainerService baubleContainerService
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.playerRef = playerRef;
        this.itemExpService = itemExpService;
        this.baubleContainerService = baubleContainerService;
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
        // Append the Bauble button from its own UI file (so event binding targets a real element)
        uiCommandBuilder.append("#BaubleButtonContainer", "EOO_Bauble_Button.ui");

        // Get the player's inventory
        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            uiCommandBuilder.set("#ItemCount.Text", "Error: No player");
            return;
        }

        final Inventory inventory = playerComponent.getInventory();
        final int activeHotbarSlot = inventory.getActiveHotbarSlot();

        // Collect all XP-gaining items: hotbar, storage, backpack, armor, and bauble (rings/amulets)
        this.weaponsList = new ArrayList<>();
        collectWeapons(inventory.getHotbar(), "Hotbar", this.weaponsList, activeHotbarSlot);
        collectWeapons(inventory.getStorage(), "Storage", this.weaponsList, -1);
        collectWeapons(inventory.getBackpack(), "Backpack", this.weaponsList, -1);
        collectWeapons(inventory.getArmor(), "Armor", this.weaponsList, -1);
        // Use the ref passed to build() so we get this player's bauble container (not a stale playerRef)
        final PlayerRef playerRefForBuild = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefForBuild != null) {
            final int beforeBauble = this.weaponsList.size();
            final ItemContainer bauble = this.baubleContainerService.getOrCreate(playerRefForBuild);
            collectWeapons(bauble, "Bauble", this.weaponsList, -1);
            final int fromBauble = this.weaponsList.size() - beforeBauble;
            System.out.println("[EOO UI] Bauble: " + fromBauble + " XP-gaining item(s) (container slots=" + bauble.getCapacity() + ")");
        }

        // Sort: (1) held item first, (2) items with available embues, (3) items with XP but no embues, (4) unused at end
        this.weaponsList.sort(Comparator
                .comparing((WeaponInfo w) -> !w.isHeld)                    // held item first
                .thenComparing((WeaponInfo w) -> w.pendingEmbues <= 0)     // has available embues second
                .thenComparing((WeaponInfo w) -> w.isUnused)               // has XP (no embues) before unused
                .thenComparing((WeaponInfo w) -> w.level, Comparator.reverseOrder())
                .thenComparing((WeaponInfo w) -> w.totalXp, Comparator.reverseOrder())
        );

        // Update item count
        uiCommandBuilder.set("#ItemCount.Text", this.weaponsList.size() + " items found");

        // Bauble (3-slot backpack) button - bound to the button from EOO_Bauble_Button.ui
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BaubleButtonContainer #BaubleButton",
                EventData.of("Action", "openBauble")
        );

        // Check if player is in creative mode (for debug button visibility)
        final boolean isCreativeMode = playerComponent.getGameMode() == GameMode.Creative;

        // Add an entry for each weapon
        for (int i = 0; i < this.weaponsList.size(); i++) {
            final WeaponInfo weapon = this.weaponsList.get(i);

            // Use highlighted template for the held item (first in list), normal template otherwise
            final boolean useHeldTemplate = (i == 0 && weapon.isHeld);
            uiCommandBuilder.append("#ItemList", useHeldTemplate ? "EOO_Item_Entry_Held.ui" : "EOO_Item_Entry.ui");

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
                System.out.println(String.format(
                        "[EOO UI] %s: Level=%d, PendingEmbues=%d",
                        weapon.item.getItemId(), weapon.level, weapon.pendingEmbues
                ));
                if (weapon.pendingEmbues > 0) {
                    uiCommandBuilder.set(sel + " #EmbueButton.Visible", true);
                    uiCommandBuilder.set(sel + " #EmbueButton.Text", "+" + weapon.pendingEmbues + " Embues");
                    System.out.println("[EOO UI] Showing embue button for " + weapon.item.getItemId());
                    
                    // Register click event for the embue button
                    uiEventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            sel + " #EmbueButton",
                            EventData.of("Action", "embue").append("WeaponIndex", String.valueOf(i))
                    );
                }
            }
            
            // Show debug button in creative mode (for all weapons, used or unused)
            if (isCreativeMode) {
                uiCommandBuilder.set(sel + " #DebugButton.Visible", true);
                
                // Register click event for the debug button
                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        sel + " #DebugButton",
                        EventData.of("Action", "debug").append("WeaponIndex", String.valueOf(i))
                );
            }
        }
    }

    private void collectWeapons(
            @Nonnull ItemContainer container,
            @Nonnull String containerName,
            @Nonnull List<WeaponInfo> weapons,
            int activeHotbarSlot
    ) {
        final short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            final ItemStack item = container.getItemStack(slot);
            if (item == null || item.isEmpty()) {
                if ("Bauble".equals(containerName)) {
                    System.out.println("[EOO UI] Bauble slot " + slot + ": empty");
                }
                continue;
            }
            final String itemId = item.getItemId();
            final boolean canGain = this.itemExpService.canGainXp(item);
            if ("Bauble".equals(containerName)) {
                System.out.println("[EOO UI] Bauble slot " + slot + ": " + itemId + " canGainXp=" + canGain);
            }
            if (canGain) {
                final boolean isHeld = "Hotbar".equals(containerName) && activeHotbarSlot >= 0 && slot == activeHotbarSlot;
                weapons.add(new WeaponInfo(item, containerName, slot, isHeld));
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
        final boolean isHeld;
        final int level;
        final double totalXp;
        final WeaponCategory category;
        final String categoryText;
        final String xpText;
        final String effectsText;
        final String locationText;
        final boolean isUnused;
        final int pendingEmbues;

        WeaponInfo(ItemStack item, String containerName, int slot, boolean isHeld) {
            this.item = item;
            this.containerName = containerName;
            this.slot = slot;
            this.isHeld = isHeld;
            this.level = itemExpService.getItemLevel(item);
            this.totalXp = itemExpService.getItemXp(item);
            this.isUnused = this.totalXp == 0;
            this.pendingEmbues = itemExpService.getPendingEmbues(item);
            
            // Determine weapon category from item ID (no damage event available here)
            this.category = WeaponCategoryUtil.determineCategory(null, item);
            this.categoryText = "Armor".equals(containerName)
                    ? "Armor"
                    : WeaponCategoryUtil.getDisplayName(this.category);

            // Check if at max level
            if (this.level >= itemExpService.getMaxLevel()) {
                this.xpText = "XP: -/- (MAX)";
            } else {
                final double xpForCurrent = itemExpService.getXpRequiredForLevel(level);
                final double xpForNext = itemExpService.getXpRequiredForLevel(level + 1);
                final double currentXp = this.totalXp - xpForCurrent;
                final double xpNeeded = xpForNext - xpForCurrent;
                final double percent = xpNeeded > 0 ? (currentXp / xpNeeded) * 100 : 0;

                this.xpText = String.format("XP: %.0f / %.0f (%.0f%%)", currentXp, xpNeeded, percent);
            }

            final String effects = itemExpService.getEffectsService().getEffectsSummary(item);
            this.effectsText = effects.isEmpty() ? "Effects: None" : "Effects: " + effects;

            if ("Armor".equals(containerName) && slot >= 0 && slot < ARMOR_SLOT_NAMES.length) {
                this.locationText = "Armor (" + ARMOR_SLOT_NAMES[slot] + ")";
            } else {
                this.locationText = containerName + " slot " + (slot + 1);
            }
        }
    }

    private static final String[] ARMOR_SLOT_NAMES = {"Head", "Chest", "Hands", "Legs"};

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        // Handle bauble (3-slot backpack) button click - open Bench page with our 3-slot container (shows slots)
        if ("openBauble".equals(data.action)) {
            final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
            if (playerComponent != null) {
                this.baubleContainerService.openBaubleAsBenchPage(ref, store, playerComponent);
            }
            sendUpdate();
            return;
        }

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
                                    weapon.slot,
                                    this.baubleContainerService
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
        
        // Handle debug button click (creative mode only)
        if ("debug".equals(data.action) && data.weaponIndex != null) {
            try {
                final int weaponIdx = Integer.parseInt(data.weaponIndex);
                if (weaponIdx >= 0 && this.weaponsList != null && weaponIdx < this.weaponsList.size()) {
                    final WeaponInfo weapon = this.weaponsList.get(weaponIdx);
                    final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
                    
                    if (playerComponent != null && playerComponent.getGameMode() == GameMode.Creative) {
                        // Open the debug effects page
                        final EOO_Debug_Effects_Page debugPage = new EOO_Debug_Effects_Page(
                                this.playerRef,
                                CustomPageLifetime.CanDismiss,
                                this.itemExpService,
                                weapon.containerName,
                                weapon.slot,
                                this.baubleContainerService
                        );
                        playerComponent.getPageManager().openCustomPage(ref, store, debugPage);
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
