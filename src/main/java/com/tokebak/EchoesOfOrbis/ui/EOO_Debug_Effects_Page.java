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
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;

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
    private final String containerName;
    
    private final int slot;
    
    // Store the list of applicable effects for event handling
    private List<WeaponEffectType> applicableEffects;

    public EOO_Debug_Effects_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            @Nonnull ItemExpService itemExpService,
            @Nonnull String containerName,
            int slot
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.itemExpService = itemExpService;
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
            uiCommandBuilder.set("#WeaponName.Text", "Error: No player");
            return;
        }

        final Inventory inventory = playerComponent.getInventory();
        final ItemStack weapon = this.getWeaponFromInventory(inventory);
        
        if (weapon == null) {
            uiCommandBuilder.set("#WeaponName.Text", "Error: Weapon not found");
            return;
        }
        
        final int weaponLevel = this.itemExpService.getItemLevel(weapon);
        final WeaponCategory category = WeaponCategoryUtil.determineCategory(null, weapon);
        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        
        // Set weapon info (use translation like main menu, fallback to formatItemId)
        final String weaponName = this.getWeaponDisplayName(weapon);
        uiCommandBuilder.set("#WeaponName.Text", weaponName + " [Lv. " + weaponLevel + "]");
        uiCommandBuilder.set("#CategoryInfo.Text", "Category: " + WeaponCategoryUtil.getDisplayName(category) + " | Showing applicable effects");
        
        // Get all applicable effects for this weapon category
        this.applicableEffects = this.getApplicableEffects(category, effectsService);
        
        // Add a row for each applicable effect
        for (int i = 0; i < this.applicableEffects.size(); i++) {
            final WeaponEffectType effectType = this.applicableEffects.get(i);
            final WeaponEffectDefinition definition = effectsService.getDefinition(effectType);
            final boolean isEnabled = effectsService.hasEffect(weapon, effectType);
            
            // Append the effect row template
            uiCommandBuilder.append("#EffectsList", "EOO_Debug_Effect_Row.ui");
            
            // Set values using selector
            final String sel = "#EffectsList[" + i + "]";
            
            // Build effect name with value if enabled
            String effectName = this.formatEffectName(effectType);
            if (isEnabled && definition != null) {
                final int effectLevel = Math.max(1, weaponLevel - 1);
                effectName += " (" + definition.getFormattedDescription(effectLevel) + ")";
            }
            
            uiCommandBuilder.set(sel + " #EffectName.Text", effectName);
            uiCommandBuilder.set(sel + " #EffectDesc.Text", this.getEffectDescription(effectType));
            
            // Set toggle button visibility based on state (show ON or OFF button)
            if (isEnabled) {
                uiCommandBuilder.set(sel + " #ToggleOn.Visible", true);
                uiCommandBuilder.set(sel + " #ToggleOff.Visible", false);
            } else {
                uiCommandBuilder.set(sel + " #ToggleOn.Visible", false);
                uiCommandBuilder.set(sel + " #ToggleOff.Visible", true);
            }
            
            // Register click events for both toggle buttons
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel + " #ToggleOn",
                    EventData.of("Toggle", String.valueOf(i))
            );
            uiEventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    sel + " #ToggleOff",
                    EventData.of("Toggle", String.valueOf(i))
            );
        }
        
        // Max level button
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#MaxLevelButton",
                EventData.of("MaxLevel", "true")
        );
        
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
     * Toggle an effect on or off.
     * Returns true if the toggle was successful.
     */
    private boolean toggleEffect(int effectIndex, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (this.applicableEffects == null || effectIndex < 0 || effectIndex >= this.applicableEffects.size()) {
            return false;
        }
        
        final WeaponEffectType effectType = this.applicableEffects.get(effectIndex);
        
        // Get the player's inventory
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
        final boolean currentlyEnabled = effectsService.hasEffect(weapon, effectType);
        
        if (currentlyEnabled) {
            // Remove the effect
            weapon = effectsService.removeEffect(weapon, effectType);
            System.out.println("[DEBUG] Disabled effect: " + effectType.getId() + " on " + weapon.getItemId());
        } else {
            // Add the effect at current weapon level
            final int weaponLevel = this.itemExpService.getItemLevel(weapon);
            weapon = effectsService.updateEffectForLevel(weapon, effectType, weaponLevel);
            System.out.println("[DEBUG] Enabled effect: " + effectType.getId() + " at level " + weaponLevel + " on " + weapon.getItemId());
        }
        
        // Update the weapon in inventory
        this.setWeaponInInventory(inventory, weapon);
        
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
     * Set the weapon in the inventory.
     */
    private void setWeaponInInventory(Inventory inventory, ItemStack weapon) {
        final ItemContainer container = this.getContainer(inventory);
        if (container != null) {
            container.setItemStackForSlot((short) this.slot, weapon);
        }
    }
    
    /**
     * Get the container from inventory by name.
     */
    @Nullable
    private ItemContainer getContainer(Inventory inventory) {
        return switch (this.containerName) {
            case "Hotbar" -> inventory.getHotbar();
            case "Storage" -> inventory.getStorage();
            case "Backpack" -> inventory.getBackpack();
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
        return this.formatName(type.getId().replace("_", " "));
    }
    
    private String getEffectDescription(WeaponEffectType type) {
        return switch (type) {
            case DAMAGE_PERCENT -> "Bonus damage as percentage of hit";
            case DURABILITY_SAVE -> "Chance to not lose durability when hitting";
            case LIFE_LEECH -> "Heal for a portion of damage dealt";
            case CRIT_CHANCE -> "Increased chance to deal critical hits";
            case CRIT_DAMAGE -> "Increased critical hit damage multiplier";
            case FIRE_ON_HIT -> "Chance to set enemies on fire";
            case POISON_ON_HIT -> "Chance to poison enemies";
            case STUN_ON_HIT -> "Chance to stun enemies";
            case SLOW_ON_HIT -> "Chance to slow enemy movement";
            case FREEZE_ON_HIT -> "Chance to freeze enemies in place";
            case BLEEDING -> "Bonus damage to bleeding targets";
            case AMMO_SAVE -> "Chance to not consume ammo";
            case MULTISHOT -> "Chance to fire extra projectiles";
            case PROJECTILE_SPEED -> "Increased projectile velocity";
            case PIERCING -> "Projectiles pierce through enemies";
            case MANA_COST_REDUCTION -> "Reduced mana cost for spells";
            case CHAIN_SPELL -> "Spells chain to nearby enemies";
            case SPELL_AREA -> "Increased spell area of effect";
            case COOLDOWN_REDUCTION -> "Reduced spell cooldowns";
            case ATTACK_SPEED -> "Increased attack speed";
            case KNOCKBACK -> "Increased knockback on hit";
            case HOMING -> "Projectiles track targets";
            case PLAYER_STAT -> "Modifies player stats while held";
        };
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        // Debug logging
        System.out.println("[DEBUG] handleDataEvent called - toggle: " + data.toggle + ", close: " + data.close + ", maxLevel: " + data.maxLevel);
        
        // Handle close
        if ("true".equals(data.close)) {
            final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
            if (playerComponent != null) {
                playerComponent.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Handle max level
        if ("true".equals(data.maxLevel)) {
            this.setWeaponToMaxLevel(ref, store);
        }
        
        // Handle toggle
        if (data.toggle != null) {
            try {
                final int effectIndex = Integer.parseInt(data.toggle);
                this.toggleEffect(effectIndex, ref, store);
            } catch (NumberFormatException ignored) {
                // Invalid effect index
            }
        }
        
        // Rebuild the entire UI to reflect changes
        this.rebuild();
    }
    
    /**
     * Set the weapon to max level (25) by setting its XP.
     */
    private void setWeaponToMaxLevel(Ref<EntityStore> ref, Store<EntityStore> store) {
        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        
        final Inventory inventory = playerComponent.getInventory();
        ItemStack weapon = this.getWeaponFromInventory(inventory);
        
        if (weapon == null) {
            return;
        }
        
        // Get XP required for level 25 and set it
        final double xpForMaxLevel = this.itemExpService.getXpRequiredForLevel(25);
        weapon = weapon.withMetadata(ItemExpService.META_KEY_XP, com.hypixel.hytale.codec.Codec.DOUBLE, xpForMaxLevel);
        
        // Also update all enabled effects to max level
        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        for (final WeaponEffectType effectType : WeaponEffectType.values()) {
            if (effectsService.hasEffect(weapon, effectType)) {
                weapon = effectsService.updateEffectForLevel(weapon, effectType, 25);
            }
        }
        
        this.setWeaponInInventory(inventory, weapon);
        System.out.println("[DEBUG] Set weapon to max level (25) with XP: " + xpForMaxLevel);
    }

    public static class Data {
        public String toggle;
        public String close;
        public String maxLevel;
        
        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(
                        new KeyedCodec<>("Toggle", Codec.STRING),
                        (d, v) -> d.toggle = v,
                        d -> d.toggle
                ).add()
                .append(
                        new KeyedCodec<>("Close", Codec.STRING),
                        (d, v) -> d.close = v,
                        d -> d.close
                ).add()
                .append(
                        new KeyedCodec<>("MaxLevel", Codec.STRING),
                        (d, v) -> d.maxLevel = v,
                        d -> d.maxLevel
                ).add()
                .build();
    }
}
