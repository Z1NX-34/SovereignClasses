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

Editor line formats:

| Section | One entry per line | Example |
|---|---|---|
| Attributes | `attribute amount [operation]` | `generic.max_health 4` · `generic.attack_damage 15%` · `generic.movement_speed 0.1 multiply_total` |
| Kit | `item [count] [nbt]` | `minecraft:iron_sword 1` (armour is equipped automatically) |
| Abilities | `Name \| Description [\| type:key=value;key=value]` | `Silent Step \| Sneak at walking speed` |
| Commands | command without `/`, `@s` = the player | `effect give @s minecraft:speed 30 1` |

Operations: `addition` (default), `multiply_base`, `multiply_total`. A percentage like `15%` means `multiply_base` with 0.15.
Abilities are display text for now; the optional `type`/`params` part is stored so scripted abilities can be added later without redoing your classes.

## JSON format
See `examples/`. A class has `name, description, order, tags, icon, attributes, kit, abilities, commands`.
A race has `name, description, order, image, allowed_classes, attributes, kit, abilities, commands`
(`allowed_classes` empty = every class).

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
