package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.EchoesOfOrbis.commands.EooCommand;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;

import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.systems.ItemExpDamageSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class EchoesOfOrbis extends JavaPlugin {

    private final Config<EchoesOfOrbisConfig> config;
    private WeaponEffectsService weaponEffectsService;
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
        
        // Initialize services
        // WeaponEffectsService manages effect definitions, processors, and application
        this.weaponEffectsService = new WeaponEffectsService();

        // ItemExpService handles XP/leveling and coordinates with effects service
        this.itemExpService = new ItemExpService(cfg, this.weaponEffectsService);
        
        // Register the damage system that processes combat events
        this.getEntityStoreRegistry().registerSystem(
                (ISystem) new ItemExpDamageSystem(this.itemExpService, cfg)
        );

        this.getCommandRegistry().registerCommand(new EooCommand(this.itemExpService));

        System.out.println("[EOO]: Echoes of Orbis is loaded!");
        System.out.println("[EOO]: Weapon Effects System initialized with default effects:");
        System.out.println("[EOO]:   - DAMAGE_PERCENT: +5% damage per weapon level");

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            event.getPlayer().sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded, /eoo for UI"));
        });
    }
}
