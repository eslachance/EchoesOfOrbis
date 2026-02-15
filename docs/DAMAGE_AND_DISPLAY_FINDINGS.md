# Damage Override & Display Findings (HytaleServer)

Findings from digging into `libs/HytaleServer` for:
1. **Overriding base damage** instead of creating a second damage event
2. **Affecting how damage is shown** (e.g. "20+2", colors, strings)

---

## 1. Can we override the base damage value?

**Yes.** The engine already does this in several places.

### Damage class (`Damage.java`)

- **`getAmount()` / `setAmount(float amount)`** – The amount is mutable.
- **`getInitialAmount()`** – Read-only initial value (set in constructor).
- Other systems **modify the same `Damage` instance** before it is applied.

### Engine systems that call `setAmount()`

| System | What it does |
|--------|----------------|
| **WieldingDamageReduction** | `damage.setAmount(damage.getAmount() * wieldingModifier * angledWieldingModifier)` |
| **ArmorDamageReduction** | `damage.setAmount(amount)` after flat/mult reduction |
| **ApplyDamage** | `damage.setAmount((float) Math.round(damage.getAmount()))` then subtracts from health |

So the intended pattern is: **one Damage event, multiple systems in the pipeline adjust `amount` in place.**

### System order (DamageModule)

Rough pipeline:

1. **Gather** – Damage events are invoked (e.g. from combat).
2. **Filter** – `OrderGatherFilter`, then e.g. `FilterPlayerWorldConfig`, `FilterUnkillable`, `PlayerDamageFilterSystem`, **WieldingDamageReduction**, **ArmorDamageReduction**, etc.  
   Systems in **Filter** can read and modify `damage` (including `setAmount()`).
3. **ApplyDamage** – Depends on `AFTER` Filter. It rounds `damage.getAmount()` and subtracts from health. So whatever amount we set in Filter is what gets applied.
4. **Inspect** – `PlayerHitIndicators`, `EntityUIEvents`, etc. They run after ApplyDamage and use `damage.getAmount()` for display.

So for our mod:

- We should run in the **Filter** phase (same conceptual phase as `WieldingDamageReduction` / `ArmorDamageReduction`).
- In our `DamageEventSystem.handle()`:  
  - Read `float base = damage.getAmount()`.  
  - Compute bonus (e.g. percent from weapon effect).  
  - Call **`damage.setAmount(base + bonus)`** (or `base * (1 + percent)`).  
- Then **ApplyDamage** will apply that total, and we no longer need a second damage event for bonus damage.

We need to register our system so it runs in Filter (e.g. declare a dependency on `DamageModule.get().getFilterDamageGroup()` with `Order.AFTER`, and no dependency on ApplyDamage, so we run before ApplyDamage). Our `ItemExpDamageSystem` is currently registered without a group; we should give it `getFilterDamageGroup()` (or equivalent) so it runs in Filter and can safely call `setAmount()`.

---

## 2. Can we affect how damage is shown (e.g. "20+2", colors)?

**Partially.** The protocol allows custom **text** for the floating combat number, but there are two display paths and no built-in “override” hook.

### Two display paths

| Path | Packet / type | Content | Who sends it |
|------|----------------|--------|---------------|
| **Attacker (reticle)** | **DamageInfo** (packet 112) | `damageSourcePosition`, **damageAmount** (float), `damageCause` | **PlayerHitIndicators** |
| **Victim (floating number)** | **CombatTextUpdate** (component update) | **hitAngleDeg**, **text** (String) | **EntityUIEvents** |

So:

- **DamageInfo** only has a single **float** `damageAmount`. No string, no color. We cannot send "20+2" or a custom color through this packet without changing the protocol.
- **CombatTextUpdate** has **`String text`** (and `hitAngleDeg`). So the floating combat text **can** be any string (e.g. `"20+2"`). The engine currently sends `Integer.toString((int) Math.floor(damageAmount))`.

### How the engine sends combat text

In **DamageSystems.EntityUIEvents**:

```java
DamageSystems.EntityUIEvents.queueUpdateFor(
    archetypeChunk.getReferenceTo(index),
    damage.getAmount(),  // single float
    hitAngleDeg,
    sourceEntityViewerComponent
);

private static void queueUpdateFor(..., float damageAmount, ...) {
    CombatTextUpdate update = new CombatTextUpdate(
        hitAngleDeg == null ? 0.0f : hitAngleDeg.floatValue(),
        Integer.toString((int) Math.floor(damageAmount))  // always one number
    );
    viewer.queueUpdate(ref, (ComponentUpdate) update);
}
```

So:

- The **protocol** supports custom text (and we could send `"20+2"` via `CombatTextUpdate`).
- The **engine** always builds that text from `damage.getAmount()` and does not read any meta (e.g. “display string override”) from `Damage`.

### What we can do without patching the engine

1. **Override amount only (no custom string)**  
   - Run in Filter and do `damage.setAmount(base + bonus)`.  
   - Both **DamageInfo** and **CombatTextUpdate** will then show the **same total** (e.g. `22`). No "20+2", but one number that includes our bonus.

2. **Custom string "20+2" (floating combat text only)**  
   - We could add our own system that runs in the **Inspect** phase and, for the same damage event, queues a **CombatTextUpdate** with `text = "20+2"` (and the same hit angle).  
   - Problem: **EntityUIEvents** will still run and queue a second **CombatTextUpdate** with `text = "22"`. So the client may receive **two** combat text updates per hit (e.g. "22" and "20+2"). Whether that shows as two popups or one depends on client behavior; we don’t control that.  
   - So we can *send* a custom string, but we cannot cleanly *replace* the vanilla one without changing the engine (e.g. making EntityUIEvents skip sending when we set a meta key, or running our system instead of EntityUIEvents).

### Colors

- **EntityUIComponent** (asset/config) has **combatTextColor** and other combat-text options. That config is per entity-type (e.g. default combat text color for that entity).
- **CombatTextUpdate** (the per-hit update) has only **hitAngleDeg** and **text**. There is **no color (or other styling) field** in the update. So we cannot change color per hit through the current protocol without a protocol/engine change.

---

## Summary

| Question | Answer |
|----------|--------|
| **Override base damage in place?** | **Yes.** Use `damage.setAmount(...)` in a `DamageEventSystem` that runs in the **Filter** phase (e.g. in `DamageModule.get().getFilterDamageGroup()`). Then ApplyDamage and all display systems will see the new amount. No need for a second damage event. |
| **Show a single combined number (e.g. 22)?** | **Yes.** Same as above: set total amount; DamageInfo and CombatTextUpdate will both show that number. |
| **Show "20+2" (base + bonus) in the floating combat text?** | **Possible but messy.** We can send a second **CombatTextUpdate** with `text = "20+2"` from our own system, but the engine will still send one with `"22"`, so the client may get two popups. Clean "20+2" only would require engine (or client) support for a display override (e.g. meta on Damage or a way to suppress the default combat text). |
| **Change damage number color per hit?** | **No.** CombatTextUpdate has no color field; color is from entity UI config, not per-hit. |

**Recommendation:** Use **in-place override** (`damage.setAmount(base + bonus)`) in Filter phase for correct behavior and a single displayed number. If we want "20+2" or per-hit colors later, that would need either a custom packet/client support or engine changes (e.g. a Damage meta key for “display text override” that EntityUIEvents and/or the client respect).
