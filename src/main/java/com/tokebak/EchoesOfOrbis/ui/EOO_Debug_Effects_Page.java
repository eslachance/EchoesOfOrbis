package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
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
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.utils.EOOTranslations;
import com.tokebak.EchoesOfOrbis.utils.EooLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug UI page for enabling/disabling weapon effects in creative mode.
 * This allows developers to quickly test effects without having to level up weapons.
 */
public class EOO_Debug_Effects_Page extends InteractiveCustomUIPage<EOO_Debug_Effects_Page.Data> {

    @Nonnull
    private final ItemExpService itemExpService;
    @Nonnull
    private final PlayerRef playerRef;
    @Nonnull
    private final BaubleContainerService baubleContainerService;
    @Nonnull
    private final String containerName;
    private final int slot;

    // Store the list of applicable effects for event handling
    private List<WeaponEffectType> applicableEffects;

    public EOO_Debug_Effects_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull ItemExpService itemExpService,
            @Nonnull String containerName,
            int slot,
            @Nonnull BaubleContainerService baubleContainerService
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.itemExpService = itemExpService;
        this.playerRef = playerRef;
        this.baubleContainerService = baubleContainerService;
        this.containerName = containerName;
        this.slot = slot;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder uiCommandBuilder,
            @Nonnull UIEventBuilder uiEventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        // Load the UI file
        uiCommandBuilder.append("EOO_Debug_Effects_Page.ui");

        // Get the player's inventory
        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            uiCommandBuilder.set("#WeaponName.Text", EOOTranslations.ui("noPlayer", "Error: No player"));
            return;
        }

        final Inventory inventory = playerComponent.getInventory();
        final ItemStack weapon = this.getWeaponFromInventory(inventory);
        
        if (weapon == null) {
            uiCommandBuilder.set("#WeaponName.Text", EOOTranslations.ui("weaponNotFound", "Error: Weapon not found"));
            return;
        }
        
        final int weaponLevel = this.itemExpService.getItemLevel(weapon);
        final WeaponCategory category = WeaponCategoryUtil.determineCategory(null, weapon);
        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        
        // Set weapon info (use translation like main menu, fallback to formatItemId)
        final String weaponName = this.getWeaponDisplayName(weapon);
        uiCommandBuilder.set("#WeaponName.Text", weaponName + " [Lv. " + weaponLevel + "]");
        uiCommandBuilder.set("#CategoryInfo.Text", EOOTranslations.ui("categoryInfo", "category", WeaponCategoryUtil.getDisplayName(category), "Category: " + WeaponCategoryUtil.getDisplayName(category) + " | Showing applicable effects"));
        
        // Get all applicable effects for this weapon category
        this.applicableEffects = this.getApplicableEffects(category, effectsService);
        
        // Add a row for each applicable effect (Add or Upgrade, like upgrade selection window)
        for (int i = 0; i < this.applicableEffects.size(); i++) {
            final WeaponEffectType effectType = this.applicableEffects.get(i);
            final WeaponEffectDefinition definition = effectsService.getDefinition(effectType);
            final boolean hasEffect = effectsService.hasEffect(weapon, effectType);
            final int effectLevel = hasEffect ? effectsService.getEffect(weapon, effectType).getLevel() : 0;
            
            // Append the effect row template
            uiCommandBuilder.append("#EffectsList", "EOO_Debug_Effect_Row.ui");
            
            // Set values using selector
            final String sel = "#EffectsList[" + i + "]";
            
            // Build effect name with value if enabled
            String effectName = this.formatEffectName(effectType);
            if (hasEffect && definition != null) {
                effectName += " (" + definition.getFormattedDescription(effectLevel) + ")";
            }
            
            uiCommandBuilder.set(sel + " #EffectName.Text", effectName);
            uiCommandBuilder.set(sel + " #EffectDesc.Text", this.getEffectDescription(effectType));
            
            // Button label: "Add" or "Upgrade (Lv.N)"
            final String buttonText = hasEffect ? ("Upgrade (Lv." + effectLevel + ")") : "Add";
            uiCommandBuilder.set(sel + " #AddUpgradeButton.Text", buttonText);
            
            // Register click: Add or Upgrade with index
            final String action = hasEffect ? "Upgrade" : "Add";
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel + " #AddUpgradeButton",
                    EventData.of("EffectAction", action + ":" + i)
            );
        }
        
        // Close button
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Close", "true")
        );
    }
    
    /**
     * Get all effect types that apply to a weapon category.
     * Includes effects that have a registered definition.
     */
    private List<WeaponEffectType> getApplicableEffects(
            WeaponCategory category,
            WeaponEffectsService effectsService
    ) {
        final List<WeaponEffectType> applicable = new ArrayList<>();
        
        for (final WeaponEffectType type : WeaponEffectType.values()) {
            // Check if effect applies to this category
            if (!type.appliesTo(category)) {
                continue;
            }
            
            // Check if we have a definition for it
            if (effectsService.getDefinition(type) == null) {
                continue;
            }
            
            applicable.add(type);
        }
        
        return applicable;
    }
    
    /**
     * Add or upgrade an effect (like the upgrade selection window).
     * Brings the item up by one level and sets XP so it's as if the player had upgraded naturally.
     * Returns true if successful.
     */
    private boolean addOrUpgradeEffect(int effectIndex, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (this.applicableEffects == null || effectIndex < 0 || effectIndex >= this.applicableEffects.size()) {
            return false;
        }

        final WeaponEffectType effectType = this.applicableEffects.get(effectIndex);

        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return false;
        }

        final Inventory inventory = playerComponent.getInventory();
        ItemStack weapon = this.getWeaponFromInventory(inventory);
        if (weapon == null) {
            return false;
        }

        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        final boolean hasEffect = effectsService.hasEffect(weapon, effectType);
        final int currentItemLevel = this.itemExpService.getItemLevel(weapon);
        final int newLevel = currentItemLevel + 1;

        if (hasEffect) {
            final WeaponEffectInstance existing = effectsService.getEffect(weapon, effectType);
            if (existing != null) {
                weapon = effectsService.setEffect(weapon, new WeaponEffectInstance(effectType, existing.getLevel() + 1));
            }
        } else {
            weapon = this.itemExpService.unlockEffect(weapon, effectType);
            weapon = effectsService.setEffect(weapon, new WeaponEffectInstance(effectType, 1));
        }

        // Set XP to the threshold for new level (as if player had leveled naturally)
        final double xpForNewLevel = this.itemExpService.getXpRequiredForLevel(newLevel);
        weapon = weapon.withMetadata(ItemExpService.META_KEY_XP, com.hypixel.hytale.codec.Codec.DOUBLE, xpForNewLevel);

        this.setWeaponInInventory(inventory, weapon);
        EooLogger.debug("%s effect %s -> item level %d (XP: %.0f)", hasEffect ? "Upgraded" : "Added", effectType.getId(), newLevel, xpForNewLevel);
        return true;
    }
    
    /**
     * Get the weapon from the inventory based on container name and slot.
     */
    @Nullable
    private ItemStack getWeaponFromInventory(Inventory inventory) {
        final ItemContainer container = this.getContainer(inventory);
        if (container == null) {
            return null;
        }
        return container.getItemStack((short) this.slot);
    }
    
    /**
     * Set the weapon in the inventory (or bauble container when containerName is "Bauble").
     */
    private void setWeaponInInventory(Inventory inventory, ItemStack weapon) {
        final ItemContainer container = this.getContainer(inventory);
        if (container != null) {
            container.setItemStackForSlot((short) this.slot, weapon);
        }
    }

    /**
     * Get the container from inventory by name (or bauble container for "Bauble").
     */
    @Nullable
    private ItemContainer getContainer(Inventory inventory) {
        if ("Bauble".equals(this.containerName)) {
            return this.baubleContainerService.getOrCreate(this.playerRef);
        }
        return switch (this.containerName) {
            case "Hotbar" -> inventory.getHotbar();
            case "Storage" -> inventory.getStorage();
            case "Backpack" -> inventory.getBackpack();
            case "Armor" -> inventory.getArmor();
            default -> null;
        };
    }
    
    /**
     * Get display name for weapon. Uses translation if available, falls back to formatItemId.
     */
    private String getWeaponDisplayName(ItemStack item) {
        final String translationKey = item.getItem().getTranslationKey();
        final String translated = Message.translation(translationKey).getAnsiMessage();
        if (translated != null && !translated.isEmpty() && !translated.equals(translationKey)) {
            return translated;
        }
        return formatItemId(item.getItem().getId());
    }

    /** Fallback for item IDs (e.g. Weapon_Sword_Iron -> Iron Sword). */
    private String formatItemId(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String cleaned = raw;
        if (cleaned.startsWith("Weapon_")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("Tool_")) cleaned = cleaned.substring(5);
        StringBuilder sb = new StringBuilder();
        String[] parts = cleaned.split("_");
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
    
    private String formatEffectName(WeaponEffectType type) {
        return EOOTranslations.effectName(type.getId(), this.formatName(type.getId().replace("_", " ")));
    }
    
    private String getEffectDescription(WeaponEffectType type) {
        final String fromModule = this.itemExpService.getEffectsService().getShortDescription(type);
        if (fromModule != null) {
            return fromModule;
        }
        return EOOTranslations.effectDescription(type.getId(), EOOTranslations.ui("notImplemented", "Not implemented"));
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        if ("true".equals(data.close)) {
            final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
            if (playerComponent != null) {
                playerComponent.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }

        // EffectAction: "Add:0" or "Upgrade:1"
        if (data.effectAction != null) {
            final String[] parts = data.effectAction.split(":", 2);
            if (parts.length == 2) {
                try {
                    final int effectIndex = Integer.parseInt(parts[1]);
                    this.addOrUpgradeEffect(effectIndex, ref, store);
                } catch (NumberFormatException ignored) {
                    // Invalid index
                }
            }
        }

        this.rebuild();
    }

    public static class Data {
        public String close;
        public String effectAction;

        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(
                        new KeyedCodec<>("Close", Codec.STRING),
                        (d, v) -> d.close = v,
                        d -> d.close
                ).add()
                .append(
                        new KeyedCodec<>("EffectAction", Codec.STRING),
                        (d, v) -> d.effectAction = v,
                        d -> d.effectAction
                ).add()
                .build();
    }
}
