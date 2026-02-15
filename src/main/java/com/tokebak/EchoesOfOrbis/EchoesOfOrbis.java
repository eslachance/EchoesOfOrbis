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
import com.tokebak.EchoesOfOrbis.services.effects.WeaponEffectsService;
import com.tokebak.EchoesOfOrbis.services.PlayerStatModifierService;
import com.tokebak.EchoesOfOrbis.systems.HudDisplaySystem;
import com.tokebak.EchoesOfOrbis.systems.ItemExpDamageSystem;
import com.tokebak.EchoesOfOrbis.systems.PlayerAttackPowerDamageSystem;
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

        // Register the custom F-key interaction for upgrade selection
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.CODEC);

        // Register the HUD display system that shows/hides the status HUD based on active weapon
        // Must be registered BEFORE ItemExpDamageSystem so it can receive XP update notifications
        this.hudDisplaySystem = new HudDisplaySystem(this.itemExpService);
        this.getEntityStoreRegistry().registerSystem(this.hudDisplaySystem);

        // Register the damage system that processes combat events
        // This handles XP gain, weapon effects, and durability save restoration
        this.getEntityStoreRegistry().registerSystem(
                new ItemExpDamageSystem(this.itemExpService, cfg, this.hudDisplaySystem)
        );

        // Apply +6% general attack power to all damage dealt by players (same idea as armor DamageClassEnhancement)
        this.getEntityStoreRegistry().registerSystem(new PlayerAttackPowerDamageSystem());

        this.getCommandRegistry().registerCommand(new EooCommand(this.itemExpService, this.baubleContainerService));

        com.hypixel.hytale.server.core.io.ServerManager.get().registerSubPacketHandlers(EooPacketHandler::new);

        System.out.println("[EOO]: Echoes of Orbis is loaded!");

        // Send welcome message when player joins and apply ring-based stats (stamina 25, health +50)
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            boolean hasStaminaRing = PlayerStatModifierService.hasStaminaRingInInventory(player.getInventory(), bauble);
            boolean hasHealthRing = PlayerStatModifierService.hasHealthRingInInventory(player.getInventory(), bauble);
            PlayerStatModifierService.updateStaminaFromRing(ref, store, hasStaminaRing);
            PlayerStatModifierService.updateHealthFromRing(ref, store, hasHealthRing);
            player.getStatModifiersManager().setRecalculate(true);
            onlinePlayers.put(player.getPlayerRef().getUuid(), player.getPlayerRef());
            player.sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded. Use /eoo to see UI"));
        });

        // When any player inventory changes, refresh ring-based stats (stamina, health)
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Player)) return;
            Player player = (Player) entity;
            Ref<EntityStore> ref = (Ref<EntityStore>) entity.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            boolean hasStaminaRing = PlayerStatModifierService.hasStaminaRingInInventory(player.getInventory(), bauble);
            boolean hasHealthRing = PlayerStatModifierService.hasHealthRingInInventory(player.getInventory(), bauble);
            PlayerStatModifierService.updateStaminaFromRing(ref, store, hasStaminaRing);
            PlayerStatModifierService.updateHealthFromRing(ref, store, hasHealthRing);
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
        boolean hasStaminaRing = PlayerStatModifierService.hasStaminaRingInInventory(player.getInventory(), bauble);
        boolean hasHealthRing = PlayerStatModifierService.hasHealthRingInInventory(player.getInventory(), bauble);
        PlayerStatModifierService.updateStaminaFromRing(ref, store, hasStaminaRing);
        PlayerStatModifierService.updateHealthFromRing(ref, store, hasHealthRing);
        player.getStatModifiersManager().setRecalculate(true);
    }
}
