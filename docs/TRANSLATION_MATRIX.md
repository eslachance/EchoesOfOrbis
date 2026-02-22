# Echoes of Orbis – Translation Matrix

This document describes how translations work in the mod and how each string maps to `Server/Languages/en-US/server.lang`.

## How server.lang works

- **File:** `src/main/resources/Server/Languages/en-US/server.lang`
- **Format:** One entry per line: `key = value` (vanilla uses spaces around `=`). Keys are case-sensitive.
- **Lookup:** The game loads this file under the **server** namespace. Code uses the full key including the `server.` prefix, e.g. `Message.translation("server.items.EOO_Crude_Ring.name")`. In the lang file you do **not** repeat the `server.` prefix; the keys in the file are the part after `server.`. So:
  - **In code:** `"server.items.EOO_Crude_Ring.name"`
  - **In server.lang:** `items.EOO_Crude_Ring.name = Crude Ring`

## Item names and descriptions (auto-parsed)

- **Source:** Item JSON under `Server/Item/Items/` uses `TranslationProperties`:
  - `"Name": "server.items.<ItemId>.name"`
  - `"Description": "server.items.<ItemId>.description"`
- **ItemId** is the asset id (e.g. `EOO_Crude_Ring`). The game’s `Item.getTranslationKey()` returns exactly that name key, so the same key is used for tooltips and for `Message.translation(...)` in code.
- **server.lang keys:** `items.<ItemId>.name` and `items.<ItemId>.description`.

| ItemId          | server.lang key (name)           | server.lang key (description)              |
|-----------------|-----------------------------------|--------------------------------------------|
| EOO_Crude_Ring  | items.EOO_Crude_Ring.name         | items.EOO_Crude_Ring.description            |

When you add new custom items, add matching `items.<ItemId>.name` and `items.<ItemId>.description` entries.

## Vanilla weapons and armor

- Vanilla items use the same system: their translation key is `server.items.<id>.name` (or whatever is in their `TranslationProperties`). Those strings live in the **game’s** Assets `Server/Languages/en-US/server.lang`.
- Our code uses `item.getItem().getTranslationKey()` and `Message.translation(translationKey).getAnsiMessage()`. If the key is missing (e.g. for a vanilla item that has no entry in our mod’s server.lang), we fall back to formatting the item ID (e.g. `Weapon_Sword_Iron` → “Iron Sword”). We do **not** need to duplicate vanilla item names in our server.lang unless we want to override them.

## Mod-specific namespace: `eoo`

All Echoes of Orbis–specific strings use the `eoo` namespace so they don’t clash with vanilla.

- **In code:** `Message.translation("server.eoo.ui.mainTitle")` etc.
- **In server.lang:** `eoo.ui.mainTitle = Echoes of Orbis`

---

## UI strings (eoo.ui.*)

Set from Java or used as reference for UI. Where the UI is built in code, we pass translated text via `Message.translation("server.eoo.ui.<key>").getAnsiMessage()` (or set the message object if the UI accepts it).

| Key | Default (en-US) |
|-----|------------------|
| eoo.ui.mainTitle | Echoes of Orbis |
| eoo.ui.itemExperience | Item Experience |
| eoo.ui.itemsFound | {count} items found |
| eoo.ui.noPlayer | Error: No player |
| eoo.ui.weaponNotFound | Error: Weapon not found |
| eoo.ui.levelLabel | [Lv. {level}] |
| eoo.ui.categoryLabel | [{category}] |
| eoo.ui.embuesAvailable | +{count} Embues |
| eoo.ui.chooseUpgrade | Choose an Upgrade |
| eoo.ui.selectOneOfThree | Select one of 3 random options |
| eoo.ui.debugEffectsTitle | [DEBUG] Effect Editor |
| eoo.ui.categoryInfo | Category: {category} \| Showing applicable effects |
| eoo.ui.setToLv25 | SET TO LV.25 |
| eoo.ui.close | CLOSE |
| eoo.ui.cancel | Cancel |
| eoo.ui.bauble | Bauble |
| eoo.ui.baubleSlots | 3 slots — use the inventory window to move items |
| eoo.ui.baubleHint | The 3-slot window can be moved; close this panel when done. |
| eoo.ui.baubleButton | Bauble (3 slots) |
| eoo.ui.debug | Debug |
| eoo.ui.xpFormat | XP: {current} / {max} ({pct}%) |
| eoo.ui.effectsNone | Effects: None |
| eoo.ui.locationFormat | Location: {location} |
| eoo.ui.effectNamePlaceholder | Effect Name |
| eoo.ui.effectDescPlaceholder | Effect description goes here |
| eoo.ui.currentValue | Current Value: {value} |
| eoo.ui.chooseEmbue | Choose an Embue |
| eoo.ui.selectBonusEffect | Select a bonus effect for your weapon |
| eoo.ui.toggleOn | ON |
| eoo.ui.toggleOff | OFF |
| eoo.ui.notImplemented | Not implemented |
| eoo.ui.powerfulEnhancement | A powerful weapon enhancement |

---

## Weapon categories (eoo.categories.*)

Used for “Physical”, “Projectile”, “Magic”, “Ring”, “Armor” (and fallback). Code can use `Message.translation("server.eoo.categories.physical")` instead of hardcoding.

| Key | Default (en-US) |
|-----|------------------|
| eoo.categories.physical | Physical |
| eoo.categories.projectile | Projectile |
| eoo.categories.magic | Magic |
| eoo.categories.ring | Ring |
| eoo.categories.armor | Armor |
| eoo.categories.unknown | Unknown |

---

## Notifications (eoo.notifications.*)

Used when sending XP gain and level-up notifications. Can be built with `Message.translation("server.eoo.notifications.levelUp").param("itemName", name).param("level", level)` (if the message supports params) or by keeping the current raw format and adding keys for future use.

| Key | Default (en-US) |
|-----|------------------|
| eoo.notifications.xpGain | +{xp} XP \| {itemName} \| {progress} |
| eoo.notifications.levelUp | LEVEL UP! {itemName} reached Level {level}! |
| eoo.notifications.upgradesAvailable | {count} Upgrade(s) available! Press F to choose! |

---

## Effect display names and descriptions (eoo.effects.<effectId>.*)

Each effect type has an **id** (e.g. `damage_percent`, `life_leech`). For UI we can use:

- **eoo.effects.<id>.name** – Short display name (e.g. “Bonus Damage”).
- **eoo.effects.<id>.description** – Template for the value line; must contain `{value}` so code can replace it (e.g. “+{value} damage”).

Current effect ids and their description templates (as in code today):

| effectId | .name (suggested) | .description (template) |
|----------|-------------------|---------------------------|
| damage_percent | Bonus Damage | +{value} damage |
| life_leech | Life Leech | Heal {value} of damage dealt |
| durability_save | Durability Save | {value} chance to save durability |
| fire_on_hit | Fire on Hit | {value} chance to burn on hit |
| poison_on_hit | Poison on Hit | {value} chance to poison on hit |
| slow_on_hit | Slow on Hit | {value} chance to slow on hit |
| freeze_on_hit | Freeze on Hit | {value} chance to freeze on hit |
| multishot | Multishot | {value} chance for extra projectile |
| armor_* (4) | (per type) | +{value} &lt;resistance type&gt; |
| ring_* (8) | (per type) | +{value} &lt;stat&gt; |

All of these can be added to server.lang as `eoo.effects.<id>.name` and `eoo.effects.<id>.description`. Code can then resolve the description via translation and replace `{value}` with the formatted number.

---

## Summary

- **server.lang format:** `key = value`; keys are **without** the `server.` prefix (the file is in the server namespace).
- **Items:** `items.<ItemId>.name` and `items.<ItemId>.description`; item JSON references `server.items.<ItemId>.name` / `.description`.
- **Vanilla items:** Already translated by the game; we only fall back to formatting the ID when translation is missing.
- **Mod strings:** Use `eoo.ui.*`, `eoo.categories.*`, `eoo.notifications.*`, and `eoo.effects.<id>.(name|description)` in server.lang, and `Message.translation("server." + key)` in code.
