package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;

import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.systems.ItemExpDamageSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class EchoesOfOrbis extends JavaPlugin {

    private final Config<EchoesOfOrbisConfig> config;
    private ItemExpService itemExpService;

    public EchoesOfOrbis(@NonNullDecl JavaPluginInit init) {
        super(init);

        // Prefer the config provided by the platform; fall back to creating it if needed.
        this.config = this.withConfig("EchoesOfOrbisConfig", EchoesOfOrbisConfig.CODEC);
    }

    @Override
    protected void setup () {
        super.setup();
        final EchoesOfOrbisConfig cfg = (EchoesOfOrbisConfig) this.config.get();
        this.itemExpService = new ItemExpService(cfg);
        this.getEntityStoreRegistry().registerSystem(
                (ISystem) new ItemExpDamageSystem(this.itemExpService, cfg)
        );

        System.out.println("[EOO]: Echoes of Orbis is loaded!");

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            event.getPlayer().sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded, /eoo for UI"));
        });
    }
}
