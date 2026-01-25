package com.tokebak.EchoesOfOrbis.services.effects;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Context passed to effect processors containing all relevant data
 * needed to apply an effect.
 * 
 * This provides a clean way to pass data to processors without
 * needing to change method signatures when adding new data.
 */
public class EffectContext {
    
    /**
     * The original damage event that triggered this effect processing.
     */
    private final Damage originalDamage;
    
    /**
     * Reference to the entity being damaged (the target).
     */
    private final Ref<EntityStore> targetRef;
    
    /**
     * Reference to the attacking entity.
     */
    private final Ref<EntityStore> attackerRef;
    
    /**
     * The player reference for the attacker (for notifications, etc).
     */
    private final PlayerRef attackerPlayerRef;
    
    /**
     * The weapon being used.
     */
    private final ItemStack weapon;
    
    /**
     * The weapon's current level.
     */
    private final int weaponLevel;
    
    /**
     * The entity store for component access.
     */
    private final Store<EntityStore> store;
    
    /**
     * Command buffer for invoking events and modifying components.
     */
    private final CommandBuffer<EntityStore> commandBuffer;
    
    private EffectContext(final Builder builder) {
        this.originalDamage = builder.originalDamage;
        this.targetRef = builder.targetRef;
        this.attackerRef = builder.attackerRef;
        this.attackerPlayerRef = builder.attackerPlayerRef;
        this.weapon = builder.weapon;
        this.weaponLevel = builder.weaponLevel;
        this.store = builder.store;
        this.commandBuffer = builder.commandBuffer;
    }
    
    // Getters
    
    @Nonnull
    public Damage getOriginalDamage() {
        return this.originalDamage;
    }
    
    /**
     * Get the original damage amount before any modifications.
     */
    public float getOriginalDamageAmount() {
        return this.originalDamage.getAmount();
    }
    
    @Nonnull
    public Ref<EntityStore> getTargetRef() {
        return this.targetRef;
    }
    
    @Nonnull
    public Ref<EntityStore> getAttackerRef() {
        return this.attackerRef;
    }
    
    @Nullable
    public PlayerRef getAttackerPlayerRef() {
        return this.attackerPlayerRef;
    }
    
    @Nonnull
    public ItemStack getWeapon() {
        return this.weapon;
    }
    
    public int getWeaponLevel() {
        return this.weaponLevel;
    }
    
    @Nonnull
    public Store<EntityStore> getStore() {
        return this.store;
    }
    
    @Nonnull
    public CommandBuffer<EntityStore> getCommandBuffer() {
        return this.commandBuffer;
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EffectContext.
     */
    public static class Builder {
        private Damage originalDamage;
        private Ref<EntityStore> targetRef;
        private Ref<EntityStore> attackerRef;
        private PlayerRef attackerPlayerRef;
        private ItemStack weapon;
        private int weaponLevel;
        private Store<EntityStore> store;
        private CommandBuffer<EntityStore> commandBuffer;
        
        public Builder originalDamage(final Damage damage) {
            this.originalDamage = damage;
            return this;
        }
        
        public Builder targetRef(final Ref<EntityStore> ref) {
            this.targetRef = ref;
            return this;
        }
        
        public Builder attackerRef(final Ref<EntityStore> ref) {
            this.attackerRef = ref;
            return this;
        }
        
        public Builder attackerPlayerRef(final PlayerRef ref) {
            this.attackerPlayerRef = ref;
            return this;
        }
        
        public Builder weapon(final ItemStack weapon) {
            this.weapon = weapon;
            return this;
        }
        
        public Builder weaponLevel(final int level) {
            this.weaponLevel = level;
            return this;
        }
        
        public Builder store(final Store<EntityStore> store) {
            this.store = store;
            return this;
        }
        
        public Builder commandBuffer(final CommandBuffer<EntityStore> commandBuffer) {
            this.commandBuffer = commandBuffer;
            return this;
        }
        
        public EffectContext build() {
            return new EffectContext(this);
        }
    }
}
