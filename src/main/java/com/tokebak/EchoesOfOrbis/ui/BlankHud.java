package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

/**
 * An empty/blank HUD used to effectively "hide" custom HUDs without passing null.
 */
public class BlankHud extends CustomUIHud {
    public BlankHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }
    
    @Override
    protected void build(@NonNullDecl UICommandBuilder uiCommandBuilder) {
        // Intentionally empty - this creates a blank HUD with no visible elements
    }
}
