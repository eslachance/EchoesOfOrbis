package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EOO_Status_Hud extends CustomUIHud {
    
    private static final int BAR_MAX_WIDTH = 396; // 400px container - 2px border on each side
    
    @Nonnull
    private final ItemExpService itemExpService;
    
    private int currentBarWidth = 0;
    private String currentXpText = "0/0 (0%)";
    
    public EOO_Status_Hud(@Nonnull PlayerRef playerRef, @Nonnull ItemExpService itemExpService) {
        super(playerRef);
        this.itemExpService = itemExpService;
    }
    
    @Override
    protected void build(@NonNullDecl UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("EOO_Status_Hud.ui");
        
        // Set initial text
        uiCommandBuilder.set("#XpText.Text", this.currentXpText);
        
        // Set initial progress bar width
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setTop(Value.of(0));
        anchor.setBottom(Value.of(0));
        anchor.setWidth(Value.of(this.currentBarWidth));
        uiCommandBuilder.setObject("#XpFill.Anchor", anchor);
    }
    
    /**
     * Update the HUD with information from the current weapon.
     * 
     * @param weapon The weapon to display, or null if no weapon
     * @param slot The hotbar slot of the weapon (needed to include pending XP)
     */
    public void updateWithWeapon(@Nullable ItemStack weapon, byte slot) {
        if (weapon == null || weapon.isEmpty() || !itemExpService.canGainXp(weapon)) {
            // No valid weapon - show empty state
            this.currentXpText = "No Weapon";
            this.currentBarWidth = 0;
            this.updateDisplay();
            return;
        }
        
        final int level = itemExpService.getItemLevel(weapon);
        // Include pending XP in the total (XP gained but not yet flushed)
        final double totalXp = itemExpService.getTotalXpWithPending(weapon, this.getPlayerRef(), slot);
        final int maxLevel = itemExpService.getMaxLevel();
        
        // Check if at max level
        if (level >= maxLevel) {
            this.currentXpText = "MAX LEVEL";
            this.currentBarWidth = BAR_MAX_WIDTH;
            this.updateDisplay();
            return;
        }
        
        // Calculate progress to next level
        final double xpForCurrentLevel = itemExpService.getXpRequiredForLevel(level);
        final double xpForNextLevel = itemExpService.getXpRequiredForLevel(level + 1);
        final double currentLevelXp = totalXp - xpForCurrentLevel;
        final double xpNeeded = xpForNextLevel - xpForCurrentLevel;
        
        // Calculate percentage and bar width
        final double progress = xpNeeded > 0 ? (currentLevelXp / xpNeeded) : 0.0;
        final int percent = (int) (progress * 100);
        this.currentBarWidth = (int) (progress * BAR_MAX_WIDTH);
        
        // Format text: "current/needed (percent%)"
        this.currentXpText = String.format("%.0f/%.0f (%d%%)", currentLevelXp, xpNeeded, percent);
        
        this.updateDisplay();
    }
    
    /**
     * Send updated values to the client.
     */
    private void updateDisplay() {
        UICommandBuilder builder = new UICommandBuilder();
        
        // Update text
        builder.set("#XpText.Text", this.currentXpText);
        
        // Update progress bar width
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setTop(Value.of(0));
        anchor.setBottom(Value.of(0));
        anchor.setWidth(Value.of(this.currentBarWidth));
        builder.setObject("#XpFill.Anchor", anchor);
        
        // Send the update to the client
        this.update(false, builder);
    }
}
