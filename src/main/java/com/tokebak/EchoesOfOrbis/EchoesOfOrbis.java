package com.tokebak.EchoesOfOrbis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import com.hypixel.hytale.server.core.util.Config;
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
import com.tokebak.EchoesOfOrbis.systems.ToolBreakBlockEventSystem;
import com.tokebak.EchoesOfOrbis.systems.ToolEntityInteractHandler;
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

        // Register custom interactions
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.ShowUpgradeSelectionInteraction.CODEC);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.EOO_ToolEntityXpInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.EOO_ToolEntityXpInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.EOO_ToolEntityXpInteraction.CODEC);
        this.getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .register(com.tokebak.EchoesOfOrbis.interactions.EOO_SickleCropXpInteraction.ID,
                        com.tokebak.EchoesOfOrbis.interactions.EOO_SickleCropXpInteraction.class,
                        com.tokebak.EchoesOfOrbis.interactions.EOO_SickleCropXpInteraction.CODEC);

        // Register the HUD display system that shows/hides the status HUD based on active weapon
        // Must be registered BEFORE ItemExpDamageSystem so it can receive XP update notifications
        this.hudDisplaySystem = new HudDisplaySystem(this.itemExpService, this.baubleContainerService);
        HudDisplaySystem.setInstance(this.hudDisplaySystem);
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

        // Tool break: durability save, XP, and drop bonus for pickaxe/shovel/axe
        this.getEntityStoreRegistry().registerSystem(
                new ToolBreakBlockEventSystem(this.itemExpService, this.weaponEffectsService, this.hudDisplaySystem)
        );

        // Tool on entity (e.g. shears on sheep): try both events to see which fires on left-click entity
        final ToolEntityInteractHandler toolEntityInteractHandler = new ToolEntityInteractHandler(
                this.itemExpService,
                this.hudDisplaySystem
        );
        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, toolEntityInteractHandler::onPlayerInteract);
        System.out.println("[EOO]: Registered ToolEntityInteractHandler for PlayerInteractEvent");

        this.getEventRegistry().registerGlobal(PlayerMouseButtonEvent.class, e -> {
            String itemId = e.getItemInHand() != null ? e.getItemInHand().getId() : "null";
            boolean hasEntity = e.getTargetEntity() != null;
            String btn = e.getMouseButton() != null ? e.getMouseButton().toString() : "null";
            System.out.println("[EOO ToolEntity] PlayerMouseButtonEvent: button=" + btn + ", targetEntity=" + hasEntity + ", item=" + itemId);
            if (hasEntity && e.getItemInHand() != null && e.getItemInHand().getTool() != null) {
                toolEntityInteractHandler.onMouseButtonEntity(e);
            }
        });
        System.out.println("[EOO]: Registered debug + handler for PlayerMouseButtonEvent");

        com.hypixel.hytale.server.core.io.ServerManager.get().registerSubPacketHandlers(EooPacketHandler::new);

        System.out.println("[EOO]: Echoes of Orbis is loaded!");

        // Send welcome message when player joins and apply ring-effect stats (stamina, health)
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            Ref<EntityStore> ref = event.getPlayerRef();
            Store<EntityStore> store = ref.getStore();
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            ItemContainer armor = player.getInventory() != null ? player.getInventory().getArmor() : null;
            double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getStaminaBonusFromArmor(armor, this.weaponEffectsService);
            double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getHealthBonusFromArmor(armor, this.weaponEffectsService);
            double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
            double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getResistMagicFromArmor(armor, this.weaponEffectsService);
            PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
            PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
            PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
            PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
            PlayerStatModifierService.updateResistProjectileFromArmor(ref, store, PlayerStatModifierService.getProjectileResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistPhysicalFromArmor(ref, store, PlayerStatModifierService.getPhysicalResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistFireFromArmor(ref, store, PlayerStatModifierService.getFireResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistGeneralFromArmor(ref, store, PlayerStatModifierService.getGeneralResistanceFromArmor(armor, this.weaponEffectsService));
            RingHealthRegenEffectApplier.applyIfHasRing(ref, store, bauble, this.weaponEffectsService);
            player.getStatModifiersManager().setRecalculate(true);
            onlinePlayers.put(player.getPlayerRef().getUuid(), player.getPlayerRef());
            player.sendMessage(Message.raw("[EOO] Echoes of Orbis Loaded. Press F to open the item experience UI."));
        });

        // When any player inventory changes, refresh ring-effect stats (stamina, health)
        // and clear pending weapon XP for hotbar slots whose item changed (fixes wrong XP bar when swapping weapons)
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            if (!(entity instanceof Player)) return;
            Player player = (Player) entity;
            Ref<EntityStore> ref = (Ref<EntityStore>) entity.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            ItemContainer eventContainer = event.getItemContainer();
            ItemContainer hotbar = player.getInventory() != null ? player.getInventory().getHotbar() : null;
            if (hotbar != null && eventContainer == hotbar) {
                clearPendingXpForChangedHotbarSlots(this.itemExpService, player.getPlayerRef(), event.getTransaction());
                // If the currently selected hotbar slot was modified (e.g. craft into slot, move item into slot), refresh the HUD
                final byte activeSlot = player.getInventory().getActiveHotbarSlot();
                if (activeSlot >= 0 && event.getTransaction().wasSlotModified((short) activeSlot)) {
                    this.hudDisplaySystem.refreshHudForCurrentSlot(ref, store, player);
                }
            }
            ItemContainer bauble = this.baubleContainerService.getOrCreate(player.getPlayerRef());
            ItemContainer armor = player.getInventory() != null ? player.getInventory().getArmor() : null;
            double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getStaminaBonusFromArmor(armor, this.weaponEffectsService);
            double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getHealthBonusFromArmor(armor, this.weaponEffectsService);
            double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
            double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService)
                    + PlayerStatModifierService.getResistMagicFromArmor(armor, this.weaponEffectsService);
            PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
            PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
            PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
            PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
            PlayerStatModifierService.updateResistProjectileFromArmor(ref, store, PlayerStatModifierService.getProjectileResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistPhysicalFromArmor(ref, store, PlayerStatModifierService.getPhysicalResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistFireFromArmor(ref, store, PlayerStatModifierService.getFireResistanceFromArmor(armor, this.weaponEffectsService));
            PlayerStatModifierService.updateResistGeneralFromArmor(ref, store, PlayerStatModifierService.getGeneralResistanceFromArmor(armor, this.weaponEffectsService));
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

    /**
     * When a hotbar slot's item is replaced by a different item, pending XP for that slot
     * belonged to the previous item. Clear it so the HUD shows the new item's XP correctly.
     */
    private static void clearPendingXpForChangedHotbarSlots(
            ItemExpService itemExpService,
            PlayerRef playerRef,
            Transaction transaction
    ) {
        for (SlotTransaction slotTx : collectSlotTransactions(transaction)) {
            if (!slotTx.succeeded()) continue;
            ItemStack before = slotTx.getSlotBefore();
            ItemStack after = slotTx.getSlotAfter();
            if (isDifferentItem(before, after)) {
                itemExpService.clearPendingXp(playerRef, (byte) slotTx.getSlot());
            }
        }
    }

    private static List<SlotTransaction> collectSlotTransactions(Transaction transaction) {
        List<SlotTransaction> out = new ArrayList<>();
        collectSlotTransactions(transaction, out);
        return out;
    }

    private static void collectSlotTransactions(Transaction transaction, List<SlotTransaction> out) {
        if (transaction instanceof SlotTransaction) {
            out.add((SlotTransaction) transaction);
        } else if (transaction instanceof ListTransaction) {
            for (Transaction t : ((ListTransaction<?>) transaction).getList()) {
                collectSlotTransactions(t, out);
            }
        } else if (transaction instanceof MoveTransaction) {
            MoveTransaction<?> move = (MoveTransaction<?>) transaction;
            collectSlotTransactions(move.getRemoveTransaction(), out);
            collectSlotTransactions(move.getAddTransaction(), out);
        }
    }

    private static boolean isDifferentItem(ItemStack a, ItemStack b) {
        if (ItemStack.isEmpty(a) && ItemStack.isEmpty(b)) return false;
        if (ItemStack.isEmpty(a) || ItemStack.isEmpty(b)) return true;
        return !java.util.Objects.equals(a.getItemId(), b.getItemId());
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
        ItemContainer armor = player.getInventory() != null ? player.getInventory().getArmor() : null;
        double staminaBonus = PlayerStatModifierService.getStaminaBonusFromRings(bauble, this.weaponEffectsService)
                + PlayerStatModifierService.getStaminaBonusFromArmor(armor, this.weaponEffectsService);
        double healthBonus = PlayerStatModifierService.getHealthBonusFromRings(bauble, this.weaponEffectsService)
                + PlayerStatModifierService.getHealthBonusFromArmor(armor, this.weaponEffectsService);
        double healthRegenBonus = PlayerStatModifierService.getHealthRegenBonusFromRings(bauble, this.weaponEffectsService);
        double resistMagicBonus = PlayerStatModifierService.getResistMagicFromRings(bauble, this.weaponEffectsService)
                + PlayerStatModifierService.getResistMagicFromArmor(armor, this.weaponEffectsService);
        PlayerStatModifierService.updateStaminaFromRings(ref, store, staminaBonus);
        PlayerStatModifierService.updateHealthFromRings(ref, store, healthBonus);
        PlayerStatModifierService.updateHealthRegenFromRings(ref, store, healthRegenBonus);
        PlayerStatModifierService.updateResistMagicFromRings(ref, store, resistMagicBonus);
        PlayerStatModifierService.updateResistProjectileFromArmor(ref, store, PlayerStatModifierService.getProjectileResistanceFromArmor(armor, this.weaponEffectsService));
        PlayerStatModifierService.updateResistPhysicalFromArmor(ref, store, PlayerStatModifierService.getPhysicalResistanceFromArmor(armor, this.weaponEffectsService));
        PlayerStatModifierService.updateResistFireFromArmor(ref, store, PlayerStatModifierService.getFireResistanceFromArmor(armor, this.weaponEffectsService));
        PlayerStatModifierService.updateResistGeneralFromArmor(ref, store, PlayerStatModifierService.getGeneralResistanceFromArmor(armor, this.weaponEffectsService));
        RingHealthRegenEffectApplier.applyIfHasRing(ref, store, bauble, this.weaponEffectsService);
        player.getStatModifiersManager().setRecalculate(true);
    }
}
