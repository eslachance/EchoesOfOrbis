package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.EchoesOfOrbis.commands.EooCommand;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.io.EooPacketHandler;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;
import com.tokebak.EchoesOfOrbis.systems.ItemExpDamageSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class EchoesOfOrbis extends JavaPlugin {

    private final Config<EchoesOfOrbisConfig> config;
    private WeaponEffectsService weaponEffectsService;
    private ItemExpService itemExpService;
    private HudDisplaySystem hudDisplaySystem;

    public EchoesOfOrbis(@NonNullDecl JavaPluginInit init) {
        super(init);

        // Prefer the config provided by the platform; fall back to creating it if needed.
        this.config = this.withConfig("EchoesOfOrbisConfig", EchoesOfOrbisConfig.CODEC);
    }

    @Override
    protected void setup () {
        super.setup();
        final EchoesOfOrbisConfig cfg = this.config.get();
        
        // Initialize services
        // WeaponEffectsService manages effect definitions, processors, and application
        this.weaponEffectsService = new WeaponEffectsService();

        // ItemExpService handles XP/leveling and coordinates with effects service
        this.itemExpService = new ItemExpService(cfg, this.weaponEffectsService);
        ItemExpService.setInstance(this.itemExpService);

        // Register the custom F-key interaction for upgrade selection
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.CODEC);

        // Register the damage system that processes combat events
        // This handles XP gain, weapon effects, and durability save restoration
        this.getEntityStoreRegistry().registerSystem(
                new ItemExpDamageSystem(this.itemExpService, cfg)
        );

        // Register the HUD display system that shows/hides the status HUD based on active weapon
        this.hudDisplaySystem = new HudDisplaySystem(this.itemExpService);
        this.getEntityStoreRegistry().registerSystem(this.hudDisplaySystem);

        this.getCommandRegistry().registerCommand(new EooCommand(this.itemExpService));

        com.hypixel.hytale.server.core.io.ServerManager.get().registerSubPacketHandlers(EooPacketHandler::new);

        System.out.println("[EOO]: Echoes of Orbis is loaded!");

        // Send welcome message when player joins
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            player.sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded. Use /eoo to see UI"));
        });

        // Clean up tracking data when player disconnects
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            this.hudDisplaySystem.cleanupPlayer(playerRef.getUuid());
        });
    }
}
