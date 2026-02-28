# Plan: Tool Category and Tool Effects

This document outlines how to introduce a **tool category** (pickaxes, shovels, shears, etc.) and two effects for tools: **durability save** (shared with weapons/armor) and **bonus percent to items dropped**.

---

## 1. Current State Summary

- **Item categories** are defined by `WeaponCategory` (PHYSICAL, PROJECTILE, MAGIC, RING, ARMOR). `WeaponCategoryUtil.determineCategory(Damage, ItemStack)` resolves category from damage cause, item ID, and item config (`getWeapon()`, `getArmor()`, Bauble_Ring tag). Tools are **not** a category; they can gain XP via `ItemExpService.canGainXp()` because `itemConfig.getTool() != null` is allowed, but they are never classified as TOOL for effects.
- **Effects** are registered in `WeaponEffectsService`, keyed by `WeaponEffectType`. Each type has a set of `WeaponCategory` it applies to. Effects are applied in two main ways:
  - **On damage**: `ItemExpDamageSystem` → `applyOnDamageEffects(EffectContext)` → each effect’s `EffectProcessor.onDamageDealt(...)`. This path only runs when a player deals damage (weapon in hand).
  - **Rings/armor**: `PlayerStatModifierService` and related systems read effects from bauble/armor containers and apply stat modifiers; no damage event.
- **Durability save** today: `DURABILITY_SAVE` applies to `WeaponCategory.weapons()` only. When it triggers, `DurabilitySaveProcessor` sets a meta flag on the damage event; `ItemExpDamageSystem` then calls `restoreWeaponDurability()`, which uses `itemConfig.getWeapon()` and `getDurabilityLossOnHit()`. Tools are never in this path because (1) they are not in the damage path (block break, not entity damage), and (2) `restoreWeaponDurability()` explicitly returns early if `getWeapon() == null`.

---

## 2. Goals

1. **Tool category**  
   - Introduce a distinct category for tools (pickaxe, shovel, shears, etc.) so that:
     - Only tool-appropriate effects can be selected for tools (e.g. durability save, drop bonus).
     - UI and upgrade flow treat tools like weapons/armor (same XP, embue, level flow) but with tool-specific effect list.
2. **Durability save for tools**  
   - Same effect as for weapons (chance to not lose durability), but triggered when the tool **loses durability from use** (e.g. block break), not from damage. When the save triggers, restore tool durability the same way we do for weapons.
3. **Bonus percent to items dropped (tool-only)**  
   - New effect: when the player breaks a block (or harvests) **with a tool** that has this effect, apply a bonus percent to the **items dropped** from that action (e.g. +X% more drops, or +X% chance for extra drop). Exact semantics (multiply count, extra roll, etc.) can be tuned; the important part is “more drops when using this tool.”

---

## 3. Implementation Plan

### Phase 1: Add Tool Category

| Step | Location | Action |
|------|----------|--------|
| 1.1 | `WeaponCategory.java` | Add enum value `TOOL("tool")`. Add helper e.g. `tools()` returning `EnumSet.of(TOOL)` and optionally `weaponsAndTools()` if useful for shared effects. |
| 1.2 | `WeaponCategoryUtil.java` | In `determineCategory()`, **before** weapon/armor/ring logic: if `weapon != null && !weapon.isEmpty() && weapon.getItem() != null && weapon.getItem().getTool() != null` → return `WeaponCategory.TOOL`. Add a `TOOL` case in `getDisplayName()`. |
| 1.3 | `server.lang` (eoo.categories) | Add key `eoo.categories.tool = Tool` (or equivalent display name). |
| 1.4 | UI / upgrade flow | Ensure any place that builds “weapon” list for EOO (main page, embue selection, debug effects) already includes tools. `ItemExpService.canGainXp()` already allows tools; `WeaponMaterialService` and UI already handle `Tool_` prefix. Verify that when the selected item is a tool, `WeaponCategoryUtil.determineCategory(null, item)` is used with no damage (e.g. from inventory) and returns TOOL. If the UI currently uses damage to determine category, add an overload or path that determines category from item only (e.g. `getTool() != null` → TOOL, then existing logic for armor/ring/weapon). |

**Note on category without damage**  
`WeaponCategoryUtil.determineCategory(Damage, ItemStack)` is today used with a damage event. For tools in UI (no damage), call it with `damage = null`; the new early check `getTool() != null` will return TOOL before any damage-based logic.

---

### Phase 2: Durability Save for Tools

| Step | Location | Action |
|------|----------|--------|
| 2.1 | `WeaponEffectType.java` | Extend `DURABILITY_SAVE` to apply to tools. Change from `WeaponCategory.weapons()` to a set that includes both weapons and TOOL (e.g. add `WeaponCategory.weaponsAndTools()` returning `EnumSet.of(PHYSICAL, PROJECTILE, MAGIC, TOOL)` and use that for DURABILITY_SAVE). |
| 2.2 | Tool durability hook | **Discover in HytaleServer (libs/HytaleServer)**: how does the game signal “block broken with tool” and “tool durability lost”? Possibilities: a block-break or harvest event that provides (player, tool ItemStack, block type, maybe durability loss amount). Implement a **system or listener** that runs when a block is broken by a player with a tool (e.g. subscribe to block break / harvest event if the API exposes it). |
| 2.3 | Durability save for tool use | In that system: get the tool from the player’s active hotbar slot; if it’s not a tool or can’t gain XP, return. Get tool’s effects via `WeaponEffectsService.getEffects(tool)`; find `DURABILITY_SAVE` and check category TOOL; compute save chance from effect level; roll; if save, **restore tool durability** (same pattern as `ItemExpDamageSystem.restoreWeaponDurability()` but for tools). Tool durability restoration will likely use the game’s equivalent of “add durability back” (e.g. `withIncreasedDurability` if the same API exists for tools) and the game’s rule for “durability loss per block break” (may be on `itemConfig.getTool()` or similar – discover in HytaleServer/Assets). |
| 2.4 | `restoreWeaponDurability` (optional refactor) | Consider extracting a shared “restore durability for this item” helper that works for both weapon (durability loss on hit) and tool (durability loss on break), to avoid duplication. Weapon path keeps using `getDurabilityLossOnHit()`; tool path uses the discovered “durability loss per break” value. |

**Discovery note**  
If the game does not expose a “block broken with tool” event with the tool stack, we may need to hook after the fact (e.g. “after block break” + “player’s active item is a tool” + “that tool lost durability this tick”) and infer durability loss. Prefer an explicit event that provides the tool and the break.

---

### Phase 3: Bonus Percent to Items Dropped (Tool-Only Effect)

| Step | Location | Action |
|------|----------|--------|
| 3.1 | `WeaponEffectType.java` | Add a new effect type, e.g. `TOOL_DROP_BONUS("tool_drop_bonus", WeaponCategory.tools())`. Meaning: e.g. “+X% to items dropped when using this tool” (percent can scale with level). |
| 3.2 | Effect definition and processor | Create `ToolDropBonusEffectModule` (definition + processor). Processor only needs to run in the **block-break / drop** path, not in `onDamageDealt`. So either: (a) add a new callback on `EffectProcessor` for “on block broken with tool” and pass a minimal context (tool, player, drops?), or (b) keep the effect as “data only” and have the **drop modifier system** (see below) read the effect and apply the bonus. Option (b) is simpler and matches how armor/ring effects are applied by separate systems. |
| 3.3 | Drop modifier hook | **Discover in HytaleServer/Assets**: where are block break / harvest **drops** computed or given to the player? We need a hook that runs when drops are determined (or right before they are added to the player), with access to: the tool used, the block type, and the list of drops (or drop table). Then: if the tool has `TOOL_DROP_BONUS`, compute bonus (e.g. +Y% per level), and apply it. Options: multiply drop counts, add extra rolls, or add a chance for an extra copy of each drop. Document the chosen formula in the effect definition. |
| 3.4 | New system or integration | Implement a small **ToolDropBonusSystem** (or integrate into the same system as tool durability save) that: (1) runs when the drop hook fires, (2) gets the tool from the player’s hand, (3) gets `WeaponEffectsService.getEffects(tool)`, (4) finds `TOOL_DROP_BONUS` and applies to TOOL category, (5) modifies the drops (or requests extra drops) according to the effect level. |
| 3.5 | Registration and UI | Register `ToolDropBonusEffectModule` in `WeaponEffectsService.registerDefaults()`. Add lang entries for `eoo.effects.tool_drop_bonus.name` and `.description`. Ensure `getSelectableEffects(TOOL, ...)` returns this effect for tools. |

**Drop bonus formula (suggestion)**  
- Level 1: e.g. +5% to drop amount (or 5% chance for +1 extra of each drop).  
- Per-level increase: e.g. +5% per level, cap at 50%.  
- Implementation: either multiply each drop stack size by `(1 + bonusPercent)`, or add extra roll(s) with `bonusPercent` chance for one extra item per drop type. Prefer a simple, understandable rule.

---

### Phase 4: Tool XP Source (How Tools Gain XP)

Currently XP is awarded in `ItemExpDamageSystem` when a player **deals damage** with a weapon. Tools do not deal entity damage when breaking blocks, so they will **never** gain XP from the current path. To allow tools to level up:

| Step | Location | Action |
|------|----------|--------|
| 4.1 | XP on block break | In the same block-break hook used for durability save and drop bonus: when a player breaks a block with a tool that `canGainXp(tool)`, award XP to that tool (e.g. based on block type or a fixed amount per break). Reuse existing `ItemExpService.addPendingXp` / flush logic, but keyed by the same hotbar slot (tools are in hotbar). Use the same combat-idle flush and level-up flow as weapons so that tool level-ups and embue selection behave the same. |
| 4.2 | Pending XP key | Tools use the same hotbar as weapons; `getPendingXpKey(playerRef, activeSlot)` already applies. No new key needed. |

This keeps “tool category” and “tool effects” consistent: tools gain XP from use (block break), level up, get embues, and can have durability save and drop bonus.

---

## 4. File Checklist

- **Category**: `WeaponCategory.java`, `WeaponCategoryUtil.java`, `server.lang`
- **DURABILITY_SAVE for tools**: `WeaponEffectType.java`, new system (e.g. `ToolDurabilitySaveSystem`) or integration into a single “tool use” system, possibly refactor `ItemExpDamageSystem.restoreWeaponDurability` into a shared helper
- **TOOL_DROP_BONUS**: `WeaponEffectType.java`, `ToolDropBonusEffectModule` (or similar), `ToolDropBonusProcessor` (if needed), new system or same “tool use” system that also modifies drops, `WeaponEffectsService`, `server.lang`
- **Tool XP**: block-break handler (same as above), reusing `ItemExpService` and existing flush/level-up logic
- **UI**: verify main page, embue selection, and debug effects page resolve category for tools (item-only path → TOOL) and show tool-appropriate effects

---

## 5. HytaleServer / Assets Discovery

**Discovery is done.** See **Section 7** for the exact event, flow, and APIs from libs/HytaleServer. In short: use **BreakBlockEvent**; one event per block broken; tool from `getItemInHand()`; durability from `BlockHarvestUtils.calculateDurabilityUse`; bonus drops by spawning extra item entities from the same handler using `BlockHarvestUtils.getDrops` and the block's gathering config.

1. **libs/HytaleServer** (see Section 7)  
   - Event or system that runs when a block is broken by a player (e.g. “BlockBreakEvent”, “HarvestEvent”, or a “BlockHarvestSystem”).  
   - Whether the tool `ItemStack` and durability loss amount are available.  
   - Where block break drops are computed or applied (so we can add a “post-process” or “modifier” step).

2. **libs/Assets**  
   - Tool item config (e.g. durability, “durability loss per use”) if not in HytaleServer.  
   - Block drop tables if needed for implementing “bonus percent to drops” (e.g. extra roll vs multiply).

Once these are known, the “Tool durability hook” and “Drop modifier hook” steps can be implemented with the correct types and lifecycle.

---

## 7. Discovery Findings (HytaleServer Source)

The following was confirmed by reading **libs/HytaleServer** (decompiled Java).

### 7.1 BreakBlockEvent

- **Class**: `com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent`
- **Extends**: `CancellableEcsEvent` (same level as `Damage` for damage events).
- **Fired when**: A block is about to be broken. Invoked via `entityStore.invoke(ref, event)` in `BlockHarvestUtils.performBlockBreak(...)` (around line 354–368). So it runs **per block** that is broken, with the **entity ref** (e.g. player) and the **event**.
- **Event API**:
  - `getItemInHand()` → `ItemStack` (the tool in hand; can be null).
  - `getTargetBlock()` → `Vector3i` (world position).
  - `getBlockType()` → `BlockType`.
  - `setTargetBlock(Vector3i)` (can redirect which block is broken).
  - `setCancelled(boolean)` / `isCancelled()`.
- **Registration**: Same pattern as damage. Implement a system that extends `EntityEventSystem<EntityStore, BreakBlockEvent>` (cf. `DamageEventSystem extends EntityEventSystem<EntityStore, Damage>`). Register it with `getEntityStoreRegistry().registerSystem(...)` in the plugin's `setup()`.

So **BreakBlockEvent is the single hook** for: pickaxe, shovel, and any tool break. Each block broken triggers one event with the same tool in hand.

### 7.2 Where BreakBlockEvent is fired

- **Path**: Block break flow is in `BlockHarvestUtils`:
  1. **Damage path**: `performBlockDamage(...)` damages block health; when the block is destroyed it calls `performBlockBreak(world, targetBlockPos, targetBlockType, itemStack, dropQuantity, itemId, dropListId, setBlockSettings, ref, chunkReference, entityStore, chunkStore)`.
  2. **Break path**: `performBlockBreak(World, Vector3i, BlockType, ItemStack, int, String, String, int, Ref, Ref, ...)` creates `BreakBlockEvent(heldItemStack, targetBlockPosition, targetBlockTypeKey)`, calls `entityStore.invoke(ref, event)`, then if not cancelled calls `naturallyRemoveBlock(...)` which removes the block and spawns drops.

So **one BreakBlockEvent per block** that is actually broken. The tool is the same `ItemStack` for that break.

### 7.3 Shovel / axe side-effects (multiple blocks)

- **Shovel "breaks around"** and **axe "tree falls"** are implemented by the game as **multiple block breaks**. Each of those breaks goes through the same `performBlockDamage` → (when destroyed) `performBlockBreak` path. So **each additional block broken (e.g. 3×3 for shovel, or tree logs above) will fire its own BreakBlockEvent**, with the same player ref and the same `getItemInHand()` (the shovel or axe). No separate event type for "secondary" breaks.
- **Implications**:
  - **Durability save**: Each BreakBlockEvent corresponds to one durability use. So we handle durability save once per event; no need to detect "primary" vs "secondary" break.
  - **Tool XP**: Award XP once per BreakBlockEvent (or cap per "action" if desired); each event is one block broken with that tool.
  - **Drop bonus**: Each event is one block's worth of drops; we can apply bonus drops per event.
- **Physics/connected blocks**: When a block is removed, `removeBlock` can call `world.performBlockUpdate(...)` for filler/connected visuals. That updates neighbor block *state*; it does not by itself fire more BreakBlockEvents. If the game breaks additional blocks (e.g. tree fall) via a different code path that does **not** call `performBlockBreak(ref, ...)` with a player ref, those breaks would **not** fire BreakBlockEvent for a player. From the code seen, the main "player broke block with tool" path always goes through `performBlockBreak` with ref and tool, so **BreakBlockEvent is the right hook** for all player-tool breaks that go through the standard harvest system.

### 7.4 Durability loss for tools

- **Location**: `BlockHarvestUtils.calculateDurabilityUse(Item item, BlockType blockType)`. Uses `item.getTool().getDurabilityLossBlockTypes()` (or falls back to `item.getDurabilityLossOnHit()`). So tool durability loss **per break** is defined on the item/tool config.
- **Applied in**: `performBlockDamage` (around 356–358): durability is subtracted **after** the block is broken and after BreakBlockEvent has been fired. Our durability-save system should run on BreakBlockEvent and, when the save succeeds, **restore** that amount (e.g. by adding `+durability` via the same API as weapons: `withIncreasedDurability` and swap the stack).

### 7.5 Drops: no separate drop event; bonus via extra spawn

- **There is no "DropItemEvent" or "BlockDropsEvent"** in the break flow. Drops are created inside `naturallyRemoveBlock` by `BlockHarvestUtils.getDrops(blockType, quantity, itemId, dropListId)` and then `ItemComponent.generateItemDrops(...)` and `entityStore.addEntities(...)`. This runs **after** BreakBlockEvent returns (same call stack).
- **We cannot mutate the game's drop list** from the event. We **can** implement "bonus percent to items dropped" by **spawning extra item entities** from our BreakBlockEvent handler:
  1. In our `BreakBlockEvent` system, after the event is processed (we do not cancel), check the tool for `TOOL_DROP_BONUS`.
  2. From the event we have `blockType = event.getBlockType()`. Use `blockType.getGathering()` → `getBreaking()` (or `getSoft()` / `getHarvest()` for other block types). From `BlockBreakingDropType`: `getItemId()`, `getDropListId()`, `getQuantity()`.
  3. Call `BlockHarvestUtils.getDrops(blockType, bonusQuantity, itemId, dropListId)` to get the same kind of drops (including random drop lists). Use a `bonusQuantity` derived from the effect (e.g. +X% → extra roll or extra count).
  4. Spawn at the same world position: e.g. `event.getTargetBlock().toVector3d().add(0.5, 0, 0.5)`, then `ItemComponent.generateItemDrops(entityStore, bonusItemStacks, dropPosition, Vector3f.ZERO)` and `entityStore.addEntities(holders, AddReason.SPAWN)`. We need access to `EntityStore` and the chunk/store context; the system's `handle` will receive the entity ref and store (same as DamageEventSystem).

So **bonus drops = same BreakBlockEvent handler**, using `BlockHarvestUtils.getDrops` and the block's gathering config to spawn extra item entities at the block position.

### 7.6 DamageBlockEvent (optional)

- **Class**: `com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent`
- Fired **per damage tick** on the block (when the block is hit but not yet destroyed). Has `getItemInHand()`, `getTargetBlock()`, `getBlockType()`, `getDamage()` / `setDamage()`. Useful if we ever need to react to "block being damaged" rather than "block broken". For durability save and drop bonus, **BreakBlockEvent is sufficient**.

### 7.7 Tree-fall / support blocks: no BreakBlockEvent, count connected and award XP only when tree actually falls

- When a block loses support (e.g. tree trunk broken), **BlockPhysicsUtil** calls `BlockHarvestUtils.naturallyRemoveBlockByPhysics(...)` with **no player ref**. The game does **not** fire `BreakBlockEvent` for those blocks, so the mod never sees them and cannot award tool XP for "the rest of the tree."
- **Workaround**: When the player breaks a block whose type has **support-drop** physics, we **BFS** from the broken block’s 6 neighbours to count (1) total connected same-type support blocks and (2) how many of those are at the **same Y level** as the break (`sameLevelCount`). We only award falling-block XP when **sameLevelCount == 0**: i.e. there are no other supporting blocks at that level (we broke the only one), so the tree will actually fall. For a 3-wide trunk, breaking one block leaves 2 at that Y, so no bonus until the last block at that level is broken. Then we award 1× TOOL_XP_PER_BREAK for the break + totalCount × TOOL_XP_PER_BREAK for the falling blocks (capped by `MAX_CONNECTED_SUPPORT_BLOCKS`).

---

## 8. Summary

| Feature | Approach |
|--------|----------|
| **Tool category** | Add `WeaponCategory.TOOL`, detect via `item.getTool() != null` in `WeaponCategoryUtil`, add lang and ensure UI uses item-only category resolution for tools. |
| **Durability save (tools)** | Extend `DURABILITY_SAVE` to TOOL; add block-break handler that runs durability-save logic and restores tool durability when save triggers. |
| **Bonus drops (tools)** | New effect `TOOL_DROP_BONUS` for TOOL only; in BreakBlockEvent handler spawn extra item entities via `BlockHarvestUtils.getDrops` + `ItemComponent.generateItemDrops` / `addEntities`. |
| **Tool XP** | Award XP in the block-break handler when the used item is a tool, reusing existing pending XP and level-up flow. |
| **Tree-fall XP** | Support blocks: BFS counts connected same-type support blocks and sameLevelCount at break Y; only award falling XP when sameLevelCount == 0 (tree actually falls). Bonus drops: spawn via CommandBuffer.addEntity to avoid disconnect. |

All of this stays consistent with the existing effect system (categories, definitions, processors, metadata on items) and reuses the same XP/level/embue pipeline as weapons and armor.
