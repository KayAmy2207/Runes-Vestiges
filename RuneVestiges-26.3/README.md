# Rune Vestiges — Minecraft 26.3

This is a reconstructed Fabric source project based on the installed `1.0.51-mc26.3` mod JAR; it is not the original authoring source.

## Added enchantments

- **Obsidian Armor** (chestplate, level I): prevents fire and lava damage, clears burning, improves visibility in lava, and loses one durability every two seconds while the player's eyes are in lava.
- **Rune of Salvation** (chestplate, level I): saves the player's inventory on death and places it in a chest near the respawn point. The saved enchantment is removed.
- **Rejuvenation** (armor, tools, and weapons, level I): repairs nearby damaged equipment at a Rune Altar with Runic Shards and emits particles.

The enchantment books are added to their specified loot sources, and the guide lists all 35 enchantments. Rejuvenation has a dedicated shard slot in the altar.
Vanilla loot additions match the 1.21.1 branch: enchanted books can appear in abandoned mineshafts, and the Runic Compass has a 35% chance to appear in End City chests. Rune of Salvation retries chest placement every world tick if no suitable spot is initially available.

## Build

Requires Java 25. Run `.\gradlew.bat build` from this folder. The full Gradle build was verified successfully.

Minecraft 26.3 has no intermediary/Yarn mappings and publishes its game classes in the official namespace. The Loom configuration therefore disables intermediary mappings and uses the local empty `mappings.tiny` layer. A remapped sources JAR is intentionally skipped because there is no intermediary namespace.

## Limitations

- The project was reconstructed from a compiled JAR; in-game behavior has not been fully play-tested.
- Rune of Salvation stores pending inventory in server memory until a chest is placed. A server crash or restart before restoration can lose that pending inventory.
