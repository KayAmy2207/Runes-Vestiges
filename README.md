<div align="center">

# ⚡ Runes & Vestiges

**Discover 35 runic enchantments, master three ancient altars, and explore two rare Overworld structures.**

Fabric mod for **Minecraft Java 1.21.1 and 26.3**.

<img width="1289" height="816" alt="Runes & Vestiges mod artwork" src="https://github.com/user-attachments/assets/4bff81ac-856e-4071-91f1-ed9645301bd4" />

</div>

---

## Overview

Runes & Vestiges expands Minecraft's gear progression with custom enchantments, three specialized altars, a runic guide, and rare structures to discover. Find runic books in world loot and trades, or browse them in Creative mode.

The mod supports **Minecraft Java Edition 1.21.1 and 26.3** on Fabric. These are separate game versions: install the JAR that matches your Minecraft version.

## Features

### 35 runic enchantments

Enchantments cover combat, defense, movement, mining, farming, and exploration. Examples include:

- **Obsidian Armor** — a chestplate enchantment that protects against fire and lava, improves visibility while submerged in lava, and wears down while you are in lava.
- **Rune of Salvation** — a one-use chestplate enchantment that saves your inventory on death and places it in a chest near your respawn point. The enchantment is consumed.
- **Rejuvenation** — repairs your damaged enchanted gear while you are near an Enchanter and it has Runic Shards.

The complete enchantment list:

| Category | Enchantments |
| :--- | :--- |
| **Combat & defense** | Amphibian, Colossus, End Slayer, Last Stand, Lethal Edge, Life Reaper, Nightwatcher, Obsidian Armor, Rune of Salvation, Vitality, Wither Thorns |
| **Movement & mounts** | Double Jump, Horse Walker, Lift Off, Slime Jump, Sticky Pants, Spiked Harness |
| **Mining & gathering** | Archaeologist, Leaf Feller, Lumberjack, Melting, Ore Alchemy, Quarrymaster, Quick Learner, Spawner Silk, Wisdom |
| **Ranged, utility & farming** | Carry, Hooking, Hookshot, Longshot, Lucky Gardener, Magnetism, Piston Eject, Rejuvenation, Vortex |

### Three functional altars

- **Enchanter** — applies a compatible enchanted book to an item. Runic enchantments use Runic Shards.
- **Disenchanter** — extracts an enchantment from an item into a book.
- **Enchantment Anvil** — combines matching enchanted books to raise their enchantment level.

Each altar has its own interface and can also be found in Creative mode.

### Two rare Overworld structures

- **Wizard Tower** — contains loot and can bring you to the Curator of Vestiges, who trades runic books and items.
- **Shrine of Accipitridae** — an ancient sanctuary with the three altars.

The structures generate in new chunks. Explore unvisited areas or use `/locate structure runevestiges:runic_tower` with commands enabled.

### Where to find the three new enchantments

- **Obsidian Armor** books: Bastion chests.
- **Rune of Salvation** books: End City treasure chests. It is not available from the standard enchanting table.
- **Rejuvenation** books: the Wizard Tower and Shrine of Accipitridae loot.

The End City loot can also contain a **Runic Compass**, which helps locate runic structures.

### Creative mode and guide

Runic enchanted books are available in the **Runes & Vestiges** and **Tools & Utilities** tabs, and through Creative search. The written guide lists every enchantment, its effect, and compatible equipment.

## Installation

1. Install **Fabric Loader** for Minecraft 1.21.1 or 26.3.
2. Install the matching **Fabric API** for that Minecraft version.
3. Download the matching mod JAR below and put it in that profile's `mods` folder.
4. Launch the matching Fabric profile.

| Minecraft version | Mod build | Download |
| :--- | :--- | :--- |
| **1.21.1** | 1.0.32 | [runevestiges-1.0.32-mc1.21.1.jar](https://github.com/KayAmy2207/Runes-Vestiges/releases/download/v1.0.32/runevestiges-1.0.32-mc1.21.1.jar) |
| **26.3** | 1.0.52 | [runevestiges-1.0.52-mc26.3.jar](https://github.com/KayAmy2207/Runes-Vestiges/releases/download/v1.0.52/runevestiges-1.0.52-mc26.3.jar) |

See the [Releases page](../../releases) for downloads and release notes.

## Requirements

- Minecraft Java Edition **1.21.1** or **26.3**
- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api), matching your Minecraft version
- Java **21 or newer** for Minecraft 1.21.1; Java **25 or newer** for Minecraft 26.3

## Source code

- [Minecraft 1.21.1 source project](RuneVestiges-1.21.1/)
- [Minecraft 26.3 source project](RuneVestiges-26.3/)

Both projects include build instructions. The original 26.3 source project was unavailable, so its source was reconstructed from the installed mod JAR.

## Notes

- A new world is not required, but structures appear only in newly generated chunks.
- Rune of Salvation keeps a pending saved inventory in server memory until its recovery chest is placed. A server crash or restart before recovery can lose that pending inventory.
- The projects build successfully. In-game behavior may still depend on the Minecraft, Fabric Loader, and Fabric API versions in use.

## Credits & license

- **Developer:** KayAmy2207
- **License:** MIT; see [LICENSE](LICENSE).
- This Java mod is an unofficial fan-made project inspired by the Bedrock addon *More Enchants 3.0* by BLOCKLAB Studios and Eternal Creations. Code and mechanics for this project were rewritten for Java Edition.
