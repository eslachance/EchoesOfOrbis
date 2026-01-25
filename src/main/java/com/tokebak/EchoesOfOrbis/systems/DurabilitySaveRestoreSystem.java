package com.tokebak.EchoesOfOrbis.systems;

import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;

/**
 * Holds meta keys for the durability save effect.
 * 
 * The actual durability restoration is handled in ItemExpDamageSystem,
 * which pre-adds durability before the game's durability loss system runs.
 * This ensures the net effect is zero when the save triggers.
 */
public final class DurabilitySaveRestoreSystem {

    /**
     * Meta key to mark that durability should be restored.
     * Set by DurabilitySaveProcessor when the save roll succeeds.
     * Read by ItemExpDamageSystem to pre-add durability.
     */
    public static final MetaKey<Boolean> RESTORE_DURABILITY = Damage.META_REGISTRY.registerMetaObject(
            data -> Boolean.FALSE
    );

    private DurabilitySaveRestoreSystem() {
        // Utility class - no instantiation
    }
}
