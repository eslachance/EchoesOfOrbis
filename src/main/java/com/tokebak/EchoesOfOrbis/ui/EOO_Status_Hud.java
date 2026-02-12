package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;

public class EOO_Status_Hud extends CustomUIHud {
    public EOO_Status_Hud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }
    @Override
    protected void build(@NonNullDecl UICommandBuilder uiCommandBuilder) {
        uiCommandBuilder.append("EOO_Status_Hud.ui");
        uiCommandBuilder.set("#MyLabel.TextSpans", Message.raw("Status: Online"));
    }
}
