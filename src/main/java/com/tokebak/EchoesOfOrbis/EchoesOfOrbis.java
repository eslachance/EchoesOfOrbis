package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.tokebak.EchoesOfOrbis.commands.EooCommand;
import com.tokebak.EchoesOfOrbis.config.EchoesOfOrbisConfig;
import com.tokebak.EchoesOfOrbis.io.EooPacketHandler;
import com.tokebak.EchoesOfOrbis.services.BaubleContainerService;
import com.tokebak.EchoesOfOrbis.services.ItemExpService;
import com.tokebak.EchoesOfOrbis.services.PlayerStatModifierService;
import com.tokebak.EchoesOfOrbis.services.RingHealthRegenEffectApplier;
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;
import com.tokebak.EchoesOfOrbis.systems.ItemExpDamageSystem;
import com.tokebak.EchoesOfOrbis.systems.PlayerAttackPowerDamageSystem;
import com.tokebak.EchoesOfOrbis.systems.ThornsDamageSystem;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EchoesOfOrbis extends JavaPlugin {

    private final Config<EchoesOfOrbisConfig> config;
    private WeaponEffectsService weaponEffectsService;
    private ItemExpService itemExpService;
    private BaubleContainerService baubleContainerService;
    private HudDisplaySystem hudDisplaySystem;
    /** Online player UUID -> PlayerRef for bauble-change callbacks (stamina refresh). */
    private final Map<UUID, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();

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

        this.baubleContainerService = new BaubleContainerService();
        this.baubleContainerService.setStorageDir(this.getDataDirectory());
        this.baubleContainerService.setLogger(this.getLogger());
        this.baubleContainerService.setOnBaubleContainerChange(this::onBaubleContainerChanged);
        BaubleContainerService.setInstance(this.baubleContainerService);

        // Register the custom F-key interaction for upgrade selection
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.CODEC);

        // Register the HUD display system that shows/hides the status HUD based on active weapon
        // Must be registered BEFORE ItemExpDamageSystem so it can receive XP update notifications
        this.hudDisplaySystem = new HudDisplaySystem(this.itemExpService, this.baubleContainerService);
        this.getEntityStoreRegistry().registerSystem(this.hudDisplaySystem);

        // Register the damage system that processes combat events
        // This handles XP gain (weapon + rings), weapon effects, and durability save restoration
        this.getEntityStoreRegistry().registerSystem(
                new ItemExpDamageSystem(this.itemExpService, cfg, this.hudDisplaySystem, this.baubleContainerService)
        );

        // Apply attack power from ring effects (RING_ATTACK_POWER) to damage dealt by players
        this.getEntityStoreRegistry().registerSystem(
                new PlayerAttackPowerDamageSystem(this.baubleContainerService, this.weaponEffectsService)
        );

        // Thorns: reflect damage back at attacker when player is hit (RING_THORNS)
        this.getEntityStoreRegistry().registerSystem(
                new ThornsDamageSystem(this.baubleContainerService, this.weaponEffectsService)
        );

        this.getCommandRegistry().registerCommand(new EooCommand(this.itemExpService, this.baubleContainerService));

        com.hypixel.hytale.server.core.io.ServerManager.get().registerSubPacketHandlers(EooPacketHandler::new);

        System.out.println("[EOO]: Echoes of Orbis is loaded!");

        // Send welcome message when player joins and apply ring-effect stats (stamina, health)
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService);
            double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService);
            double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
            double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService);
            PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
            PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
            PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
            PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
            RingHealthRegenEffectApplier.applyIfHasRing(ref, store, bauble, this.weaponEffectsService);
            player.getStatModifiersManager().setRecalculate(true);
            onlinePlayers.put(player.getPlayerRef().getUuid(), player.getPlayerRef());
            player.sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded. Use /eoo to see UI"));
        });

        // When any player inventory changes, refresh ring-effect stats (stamina, health)
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Player)) return;
            Player player = (Player) entity;
            Ref<EntityStore> ref = (Ref<EntityStore>) entity.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService);
            double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService);
            double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
            double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService);
            PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
            PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
            PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
            PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
            RingHealthRegenEffectApplier.applyIfHasRing(ref, store, bauble, this.weaponEffectsService);
            entity.getStatModifiersManager().setRecalculate(true);
        });

        // Clean up tracking data when player disconnects (save bauble before cleanup)
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
            PlayerRef playerRef = event.getPlayerRef();
            UUID uuid = playerRef.getUuid();
            onlinePlayers.remove(uuid);
            this.baubleContainerService.savePlayer(uuid);
            this.hudDisplaySystem.cleanupPlayer(uuid);
            this.baubleContainerService.cleanupPlayer(uuid);
        });
    }

    private void onBaubleContainerChanged(UUID playerUuid) {
        PlayerRef playerRef = onlinePlayers.get(playerUuid);
        if (playerRef == null) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        ItemContainer bauble = this.baubleContainerService.getOrCreate(playerRef);
        double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService);
        double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService);
        double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
        double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService);
        PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
        PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
        PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
        PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
        RingHealthRegenEffectApplier.applyIfHasRing(ref, store, bauble, this.weaponEffectsService);
        player.getStatModifiersManager().setRecalculate(true);
    }
}
