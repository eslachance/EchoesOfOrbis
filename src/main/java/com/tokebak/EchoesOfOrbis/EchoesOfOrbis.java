package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.EchoesOfOrbis.commands.EooCommand;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.io.EooPacketHandler;
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
        ItemExpService.setInstance(this.itemExpService);

        // Register the custom F-key interaction for upgrade selection
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.CODEC);

        // Register the damage system that processes combat events
        // This handles XP gain, weapon effects, and durability save restoration
        this.getEntityStoreRegistry().registerSystem(
                (ISystem) new ItemExpDamageSystem(this.itemExpService, cfg)
        );

        this.getCommandRegistry().registerCommand(new EooCommand(this.itemExpService));

        com.hypixel.hytale.server.core.io.ServerManager.get().registerSubPacketHandlers(EooPacketHandler::new);

        System.out.println("[EOO]: Echoes of Orbis is loaded!");
        System.out.println("[EOO]: Weapon Effects System initialized (max level 25):");
        System.out.println("[EOO]:   - DAMAGE_PERCENT: 5% -> 100% (automatic)");
        System.out.println("[EOO]:   - DURABILITY_SAVE: 10% -> 100% (embue selection)");
        System.out.println("[EOO]:   - POISON_ON_HIT: 10% -> 50% chance (embue selection)");
        System.out.println("[EOO]:   - FIRE_ON_HIT: 15% -> 60% chance (embue selection)");
        System.out.println("[EOO]:   - SLOW_ON_HIT: 10% -> 50% chance (embue selection)");
        System.out.println("[EOO]:   - FREEZE_ON_HIT: 5% -> 25% chance (DISABLED)");
        System.out.println("[EOO]: Embue selection available at levels 5, 10, 15, 20, 25");

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            event.getPlayer().sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded. Press F to upgrade weapons, or /eoo for full UI"));
        });
    }
}
