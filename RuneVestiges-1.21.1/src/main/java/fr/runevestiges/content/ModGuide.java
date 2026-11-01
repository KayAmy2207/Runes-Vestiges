package fr.runevestiges.content;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

public final class ModGuide {
	private static final String GIVEN_TAG = "runevestiges_guide_given_v16";
	private static final String[] ENCHANTMENT_NAMES = {
		"Amphibian",
		"Archaeologist",
		"Carry",
		"Colossus",
		"End Slayer",
		"Hooking",
		"Hookshot",
		"Horse Walker",
		"Last Stand",
		"Leaf Feller",
		"Lumberjack",
		"Lethal Edge",
		"Longshot",
		"Lucky Gardener",
		"Magnetism",
		"Nightwatcher",
		"Melting",
		"Ore Alchemy",
		"Piston Eject",
		"Quarrymaster",
		"Quick Learner",
		"Spawner Silk",
		"Spiked Harness",
		"Sticky Pants",
		"Vortex",
		"Wisdom",
		"Wither Thorns",
		"Vitality",
		"Lift Off",
		"Double Jump",
		"Slime Jump",
		"Life Reaper",
		"Obsidian Armor",
		"Rune of Salvation",
		"Rejuvenation"
	};
	private static final String[] ENCHANTMENT_DESCRIPTIONS = {
		"I: Dolphin's Grace I\nII: Dolphin's Grace II\nIII: Dolphin's Grace III",
		"I: +10% bonus loot\nII: +20% bonus loot\nIII: +30% bonus loot",
		"I: Carry mobs up to 10 HP\nII: Carry mobs up to 20 HP\nIII: Carry mobs up to 30 HP",
		"I: +10 magic damage/hit\nII: +16 magic damage/hit\nIII: +22 magic damage/hit",
		"I: Enderman in 3 hits\nII: Enderman in 2 hits\nIII: Enderman in 1 hit",
		"I: 9 damage; pulls hooked mob\nII: 14 damage; pulls hooked mob\nIII: 19 damage; pulls hooked mob",
		"I: Pull speed 1.2\nII: Pull speed 1.3\nIII: Pull speed 1.4",
		"I: Freezes water in 3x3\nII: Freezes water in 5x5\nIII: Freezes water in 7x7",
		"I: Low health: Resistance I\nII: Low health: Resistance II\nIII: Low health: Resistance III",
		"I: Breaks up to 64 leaves\nII: Breaks up to 128 leaves\nIII: Breaks up to 192 leaves",
		"I: Fells every connected tree log; costs 1 durability per log, with no block limit",
		"I: Elytra hit: 60% max health\nII: Elytra hit: 70% max health\nIII: Elytra hit: 80% max health",
		"I: +0.1 magic damage/block\nII: +0.2 magic damage/block\nIII: +0.3 magic damage/block",
		"I: 25% chance; +1 crop item\nII: 50% chance; +1-2 crop items\nIII: 75% chance; +1-3 crop items",
		"I: Pull range 13 blocks\nII: Pull range 16 blocks\nIII: Pull range 19 blocks",
		"I: Night Vision while wearing the helmet; 10s after removal",
		"I: Smelts ore drops in 3 ticks",
		"I: 10% extra ore chance\nII: 20% extra ore chance\nIII: 30% extra ore chance",
		"I: Blocked hit: 8 damage\nII: Blocked hit: 12 damage\nIII: Blocked hit: 16 damage",
		"I: Mines a 3x3 plane\nII: Mines a 5x5 plane\nIII: Mines a 7x7 plane",
		"I: Collects 2x XP\nII: Collects 3x XP\nIII: Collects 4x XP",
		"I: Spawner costs ~30% durability\nII: Spawner costs ~20% durability\nIII: Spawner costs ~10% durability",
		"I: Nearby mobs take 6 damage/sec\nII: Nearby mobs take 9 damage/sec\nIII: Nearby mobs take 12 damage/sec",
		"I: Wall climb: 0.22 upward speed",
		"I: 15-block tornado; 1 damage/sec\nII: 20-block tornado; 2 damage/sec\nIII: 25-block tornado; 3 damage/sec",
		"I: +1 XP per block mined\nII: +2 XP per block mined\nIII: +3 XP per block mined",
		"I: Attackers: Wither I, 3s\nII: Attackers: Wither II, 4s\nIII: Attackers: Wither III, 5s",
		"I: +4 max HP (2 hearts)\nII: +8 max HP (4 hearts)\nIII: +12 max HP (6 hearts)\nIV: +16 max HP (8 hearts)\nV: +20 max HP (10 hearts)",
		"I: Elytra launch: up 4.5, forward 0.6",
		"I: Double jump: 0.75 up\nII: Double jump: 0.95 up\nIII: Double jump: 1.15 up",
		"I: Fall bounce: 0.95 strength\nII: Fall bounce: 1.00 strength\nIII: Fall bounce: 1.05 strength",
		"I: Kill heals 8% max HP\nII: Kill heals 16% max HP\nIII: Kill heals 24% max HP",
		"I: Fire and lava immunity; clear lava vision; 1 durability per 2 seconds in lava",
		"I: On death, saves inventory in a chest at the respawn point and removes this enchantment",
		"I: A nearby enchanter repairs damaged enchanted gear using runic shards"
	};
	private static final String[] ENCHANTMENT_COMPATIBILITY = {
		"Chestplates", "Shovels", "Chestplates", "Swords", "Swords", "Fishing Rods",
		"Crossbows", "Horse Armor", "Chestplates", "Shears", "Axes", "Elytra", "Bows",
		"Hoes", "Helmets", "Helmets", "Pickaxes", "Pickaxes", "Shields",
		"Pickaxes", "Pickaxes and Swords", "Pickaxes", "Horse Armor", "Leggings",
		"Tridents", "Pickaxes and Shovels", "Chestplates", "Chestplates", "Elytra",
		"Boots", "Boots", "Swords and Axes", "Chestplates", "Chestplates",
		"Armor, tools, and weapons"
	};

	private ModGuide() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			if (player.getTags().contains(GIVEN_TAG)) {
				return;
			}

			ItemStack guide = createGuideBook();
			if (!player.getInventory().add(guide)) {
				player.drop(guide, false);
			}
			player.addTag(GIVEN_TAG);
		});
	}

	public static ItemStack createGuideBook() {
		List<String> pages = new ArrayList<>();
		pages.add(
			"GUIDE DES VESTIGES\n\nTrente-cinq enchantements runiques sont disponibles dans les tours. Explorez de nouvelles régions de l'Overworld : les tours n'apparaissent que dans les chunks nouvellement générés."
		);
		pages.add(
			"LES TROIS AUTELS\n\nFaites un clic droit sur un autel pour ouvrir son interface et placer les objets dans les emplacements. Enchanteur : livre enchanté + objet compatible, 1 éclat runique et 2 niveaux. Il applique les enchantements vanilla ou runiques compatibles.\n\nDésenchanteur : objet enchanté + livre vierge. Il extrait les enchantements vanilla ou runiques, un à la fois, contre 3 niveaux."
		);
		pages.add(
			"ENCLUME D'ENCHANTEMENT\n\nOuvrez l'interface et placez deux livres portant chacun un unique enchantement identique au même niveau. L'enclume consomme les deux et produit un livre au niveau supérieur. Coût : 2 niveaux. Le niveau maximal ne peut pas être dépassé."
		);
		pages.add(
			"CRAFT : ENCHANTEUR\n\nMotif : « S », « DED », « DDD ».\nS = éclat d'écho; E = émeraude; D = briques de deepslate.\n\nCRAFT : DÉSENCHANTEUR\n\nMotif : « ESE », « DCD », « DDD ».\nE = éclat d'écho; S = catalyseur de sculk; C = obsidienne pleureuse; D = briques de deepslate."
		);
		pages.add(
			"CRAFT : ENCLUME D'ENCHANTEMENT\n\nMotif : « ACA », « DED », « DDD ».\nA = bloc d'améthyste; C = éclat d'améthyste; E = table d'enchantement; D = briques de deepslate.\n\nUne tour sur trois cache les trois appareils au dernier étage; les autres cachent leurs livres dans les coffres. Un Curateur attend près du sommet et propose six livres choisis au hasard parmi les 35 enchantements runiques. Explorez d'autres tours pour trouver d'autres offres."
		);

		String[] enchantmentIds = ModContent.enchantmentIds();
		if (ENCHANTMENT_NAMES.length != enchantmentIds.length
			|| ENCHANTMENT_DESCRIPTIONS.length != ENCHANTMENT_NAMES.length
			|| ENCHANTMENT_COMPATIBILITY.length != ENCHANTMENT_NAMES.length) {
			throw new IllegalStateException("The guide's enchantment index does not match registered enchantments.");
		}

		for (int index = 0; index < ENCHANTMENT_NAMES.length; index++) {
			pages.add(
				"ENCHANTMENT " + (index + 1) + " / " + ENCHANTMENT_NAMES.length
					+ "\n\n" + ENCHANTMENT_NAMES[index]
					+ "\n\nEffects by level:\n" + ENCHANTMENT_DESCRIPTIONS[index]
					+ "\n\nCompatible with: " + ENCHANTMENT_COMPATIBILITY[index]
					+ "\n\nMax Level: " + romanLevel(ModContent.maxEnchantmentLevel(enchantmentIds[index]))
			);
		}

		ItemStack book = new ItemStack(ModContent.VESTIGE_GUIDE);
		book.set(
			DataComponents.WRITTEN_BOOK_CONTENT,
			new WrittenBookContent(
				Filterable.passThrough("Grimoire des Vestiges"),
				"Les archivistes",
				0,
				pages.stream().map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(),
				true
			)
		);
		book.set(
			DataComponents.CUSTOM_NAME,
			Component.translatable("item.runevestiges.guide_book")
		);
		return book;
	}

	public static int enchantmentCount() {
		return ENCHANTMENT_NAMES.length;
	}

	public static String enchantmentName(int index) {
		return ENCHANTMENT_NAMES[index];
	}

	public static String enchantmentDescription(int index) {
		return ENCHANTMENT_DESCRIPTIONS[index];
	}

	public static String enchantmentCompatibility(int index) {
		return ENCHANTMENT_COMPATIBILITY[index];
	}

	public static int enchantmentMaxLevel(int index) {
		return ModContent.maxEnchantmentLevel(ModContent.enchantmentIds()[index]);
	}

	private static String romanLevel(int level) {
		return switch (level) {
			case 5 -> "V";
			case 4 -> "IV";
			case 3 -> "III";
			case 2 -> "II";
			default -> "I";
		};
	}
}
