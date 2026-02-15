package com.tokebak.EchoesOfOrbis.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Custom page shown when the player opens "Bauble" from the main menu.
 * Displays a title and Close button; the actual 3 item slots are in the native
 * container window opened alongside this page (so the player can drag items).
 */
public class EOO_Bauble_Page extends InteractiveCustomUIPage<EOO_Bauble_Page.Data> {

    @Nonnull
    private final PlayerRef playerRef;

    /** Window ID of the native 3-slot container window, so we can close it when this page closes. */
    private final int baubleWindowId;

    public EOO_Bauble_Page(
            @Nonnull PlayerRef playerRef,
            @Nonnull CustomPageLifetime lifetime,
            int baubleWindowId
    ) {
        super(playerRef, lifetime, Data.CODEC);
        this.playerRef = playerRef;
        this.baubleWindowId = baubleWindowId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder uiCommandBuilder,
            @Nonnull UIEventBuilder uiEventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        uiCommandBuilder.append("EOO_Bauble_Page.ui");

        uiEventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Close", "true")
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            Data data
    ) {
        if (!"true".equals(data.close)) {
            return;
        }

        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getWindowManager().closeWindow(ref, baubleWindowId, store);
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    public static class Data {
        public String close;

        public static final BuilderCodec<Data> CODEC = BuilderCodec
                .builder(Data.class, Data::new)
                .append(
                        new KeyedCodec<>("Close", Codec.STRING),
                        (d, v) -> d.close = v,
                        d -> d.close
                )
                .add()
                .build();
    }
}
