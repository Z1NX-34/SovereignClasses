# Sovereign Classes

Data-driven **classes** and **races** for Forge 1.20.1, with an in-game editor and a themeable gold-and-violet UI.
The mod ships **empty**: you add everything yourself, in game or as JSON.

## First run
Start the game once. The mod creates `config/sovereignclasses/`:

```
config/sovereignclasses/
  settings.json      behaviour, titles and theme
  classes/*.json     one file per class (file name = id)
  races/*.json       one file per race
```

## Editing in game (ops)
`/sovclasses editor` opens the editor (also an **Editor** button on the class and race screens for ops).
* **Classes / Races** tabs: pick an entry on the left or press **+ New**. Save writes the JSON file and updates every player at once.
* **Theme** tab: colours, hue/saturation/brightness of the textures, screen texts, and the behaviour switches.
  **Preview** shows it on your client only, **Save** stores it for everyone.

Sections of a class or race:
* **General** - description, plus tags and icon above. **Pick** opens a search list of every item or any in-game texture (item icons, GUI textures, your resource pack's art).
* **Attributes** - **+ Add attribute** opens a search list of every attribute in the pack (vanilla and modded). Each row has an amount and a mode: *Flat*, *% of base* or *% of total*.
* **Kit** - **+ Add item** opens a search list of every item. Count and optional NBT per row. Armour is equipped automatically.
* **Abilities** - name and description rows shown under *Special abilities* (scripted abilities come later).
* **Effects** - **+ Add effect** opens a search list of every potion effect. Level starts at 1; *Seconds 0* means permanent while the class or race is active (restored after death and relog).
* **Commands** - advanced: one command per line, no leading slash, `@s` is the player. Runs once when picked.
* **Classes** (races only) - tick which classes this race may use; nothing ticked = all.

## JSON format
See `examples/`. A class has `name, description, order, tags, icon, attributes, kit, abilities, effects, commands`.
A race has `name, description, order, image, allowed_classes, attributes, kit, abilities, commands`
(`allowed_classes` empty = every class).
`icon` is an item id (`minecraft:iron_sword`) or a texture path (`mypack:textures/gui/icons/knight.png`).
Effects: `{ "effect": "minecraft:night_vision", "amplifier": 0, "seconds": 0 }` (amplifier 0 = level 1, seconds 0 = permanent).

## Commands (op level 2)
* `/sovclasses editor` – open the editor
* `/sovclasses reload` – re-read all files and sync to players
* `/sovclasses reset <players>` – clear a player's race/class and show the screens again

## Behaviour switches (`settings.json`)
`require_race`, `require_class`, `race_first`, `allow_change` (let players pick again).

## Textures and race images
Every texture can be replaced from a resource pack or `kubejs/assets/sovereignclasses/textures/gui/`:
`panel.png` (32x32, border 8), `card.png` (32x32, border 6), `button_normal|hover|disabled.png` (24x24, border 6),
`corner_tl|tr|bl|br.png` (40x40), `mini.png`.
Race pictures: `textures/gui/race/<race id>.png` (square, 256x256 is good) or set `image` in the race JSON to any resource path.
Hue shift recolours the textures; set the accent colours to match.

## Build
Push to GitHub; the Actions workflow builds the jar (artifact `sovereignclasses-jar`).

## Weapons, damage rules and locks (v3)

`config/sovereignclasses/weapons.json` (editable in the editor's **Weapons** tab) holds:
- **types** - which items are a dagger, katana, axe ... Matchers: `minecraft:iron_sword`, `#minecraft:swords`, `*katana*` (any id containing the word), `@empty` (bare hands). First matching type wins, so specific types go first.
- **groups** - mob groups for rules like "Against demons". `undead`, `boss`, `player` are built in.
- **locks** - items only the listed classes may attack with, use or mine with. Creative players ignore locks.

Classes and races get, in the editor's **Weapons** and **Rules** sections (or in JSON):
- `weapon_other` and `weapons[{type, percent}]` - damage change per weapon type: `0` full, `-60` = 60 % less, `+20` = 20 % more.
- `rules[{when: dealt|taken, filter, percent}]`. Filters: `all, melee, projectile, explosion, aoe, magic, fire, fall`, `spell:fire|ice|lightning|holy|ender|blood|evocation|nature|eldritch` (Iron's Spells), `type:<damage type id>`, `vs:<group>`.

Percents of the same kind add up (+75 and -30 = +45); a total below -100 stops at zero damage. Race and class both count. Weapon damage uses the item in the main hand, so it also covers bows and staffs.
