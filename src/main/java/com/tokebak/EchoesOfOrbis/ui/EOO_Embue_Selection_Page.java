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
import com.tokebak.EchoesOfOrbis.services.effects.UpgradeOption;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategory;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponCategoryUtil;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectDefinition;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectInstance;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectType;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * UI page for selecting an upgrade for a weapon (Vampire Survivors style).
 * Shows 3 random options: boost existing effect or add new effect type.
 */
public class EOO_Embue_Selection_Page extends InteractiveCustomUIPage<EOO_Embue_Selection_Page.Data> {

    @Nonnull
    private final ItemExpService itemExpService;
    
    @Nonnull
    private final String containerName;
    
    private final int slot;
    
    // Store the available options for this selection
    private List<UpgradeOption> availableOptions;

    public EOO_Embue_Selection_Page(
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
        uiCommandBuilder.append("EOO_Embue_Selection.ui");

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
        
        // Set weapon info (use translation like main menu, fallback to formatName)
        final String weaponName = this.getWeaponDisplayName(weapon);
        uiCommandBuilder.set("#WeaponName.Text", weaponName + " [Lv. " + weaponLevel + "]");
        uiCommandBuilder.set("#Title.Text", "Choose an Upgrade");
        uiCommandBuilder.set("#Description.Text", "Select one of 3 random options");
        
        // Get available options (use persisted if any, else generate and store)
        final ItemExpService.UpgradeOptionsResult result = this.itemExpService.getOrCreatePendingUpgradeOptions(weapon, category, 3);
        this.availableOptions = result.options;
        if (result.weaponToPersist != null) {
            this.setWeaponInInventory(inventory, result.weaponToPersist);
        }
        
        // Set up each option
        for (int i = 0; i < 3; i++) {
            final String optionId = "#Option" + (i + 1);
            
            if (i < this.availableOptions.size()) {
                final UpgradeOption option = this.availableOptions.get(i);
                final WeaponEffectType effectType = option.getEffectType();
                final WeaponEffectDefinition definition = effectsService.getDefinition(effectType);
                
                if (definition != null) {
                    final String valueText;
                    if (option instanceof UpgradeOption.BoostOption boost) {
                        final String current = definition.getFormattedDescription(boost.getCurrentLevel());
                        final String next = definition.getFormattedDescription(boost.getCurrentLevel() + 1);
                        valueText = current + " -> " + next;
                    } else {
                        valueText = definition.getFormattedDescription(1);
                    }
                    
                    uiCommandBuilder.set(optionId + "Name.Text", this.formatEffectName(effectType));
                    uiCommandBuilder.set(optionId + "Value.Text", valueText);
                    uiCommandBuilder.set(optionId + "Desc.Text", this.getEffectDescription(effectType));
                    uiCommandBuilder.set(optionId + ".Visible", true);
                    
                    uiEventBuilder.addEventBinding(
                            CustomUIEventBindingType.Activating,
                            optionId,
                            EventData.of("SelectedOption", String.valueOf(i))
                    );
                }
            } else {
                uiCommandBuilder.set(optionId + ".Visible", false);
            }
        }
        
        // Cancel button
        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating, 
                "#CancelButton", 
                EventData.of("Cancel", "true")
        );
    }
    
    /**
     * Handle upgrade selection from event data.
     */
    private void selectUpgrade(int optionIndex, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (this.availableOptions == null || optionIndex < 0 || optionIndex >= this.availableOptions.size()) {
            return;
        }
        
        final UpgradeOption option = this.availableOptions.get(optionIndex);
        final WeaponEffectType effectType = option.getEffectType();
        final WeaponEffectsService effectsService = this.itemExpService.getEffectsService();
        
        final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        
        final Inventory inventory = playerComponent.getInventory();
        ItemStack weapon = this.getWeaponFromInventory(inventory);
        
        if (weapon == null) {
            return;
        }
        
        if (option instanceof UpgradeOption.BoostOption) {
            final WeaponEffectInstance existing = effectsService.getEffect(weapon, effectType);
            if (existing != null) {
                final WeaponEffectInstance upgraded = new WeaponEffectInstance(effectType, existing.getLevel() + 1);
                weapon = effectsService.setEffect(weapon, upgraded);
            }
        } else {
            weapon = this.itemExpService.unlockEffect(weapon, effectType);
            weapon = effectsService.setEffect(weapon, new WeaponEffectInstance(effectType, 1));
        }
        
        weapon = this.itemExpService.consumePendingEmbue(weapon);
        weapon = this.itemExpService.clearPendingUpgradeOptions(weapon);
        this.setWeaponInInventory(inventory, weapon);
        
        System.out.println(String.format(
                "[ItemExp] Upgrade selected: %s for %s",
                effectType.getId(),
                weapon.getItemId()
        ));
        
        playerComponent.getPageManager().setPage(ref, store, Page.None);
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
            default -> "A powerful weapon enhancement";
        };
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        // Handle cancel
        if ("true".equals(data.cancel)) {
            final Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
            if (playerComponent != null) {
                playerComponent.getPageManager().setPage(ref, store, Page.None);
            }
            return;
        }
        
        // Handle selection
        if (data.selectedOption != null) {
            try {
                final int optionIndex = Integer.parseInt(data.selectedOption);
                if (optionIndex >= 0 && optionIndex < 3) {
                    this.selectUpgrade(optionIndex, ref, store);
                }
            } catch (NumberFormatException ignored) {
                // Invalid option index
            }
        }
    }

    public static class Data {
        public String selectedOption;
        public String cancel;
        
        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(
                        new KeyedCodec<>("SelectedOption", Codec.STRING),
                        (d, v) -> d.selectedOption = v,
                        d -> d.selectedOption
                ).add()
                .append(
                        new KeyedCodec<>("Cancel", Codec.STRING),
                        (d, v) -> d.cancel = v,
                        d -> d.cancel
                ).add()
                .build();
    }
}
