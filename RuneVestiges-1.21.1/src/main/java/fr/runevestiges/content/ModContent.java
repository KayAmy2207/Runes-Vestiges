package fr.runevestiges.content;

import fr.runevestiges.RuneVestiges;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class ModContent {
	private static final String[] ENCHANTMENTS = {
		"amphibian",
		"archaeologist",
		"carry",
		"colossus",
		"end_slayer",
		"hooking",
		"hookshot",
		"horse_walker",
		"last_stand",
		"leaf_feller",
		"lumberlord",
		"lethal_edge",
		"longshot",
		"lucky_gardener",
		"magnetism",
		"nightwatcher",
		"melting",
		"ore_alchemy",
		"piston_eject",
		"quarrymaster",
		"quick_learner",
		"spawner_silk",
		"spiked_harness",
		"sticky_pants",
		"vortex",
		"wisdom",
		"wither_thorns",
		"endurance",
		"lift_off",
		"springheel",
		"soft_landing",
		"vampiric",
		"obsidian_armor",
		"rune_of_salvation",
		"rejuvenation"
	};
	private static final Set<String> ACTIVE_ENCHANTMENTS = Set.of(ENCHANTMENTS);
	public static final CustomModelData FLOATING_RUNIC_BOOK_MODEL_DATA = new CustomModelData(2);

	public static final Block RUNE_ALTAR = Registry.register(
		BuiltInRegistries.BLOCK,
		id("rune_altar"),
		new RuneAltarBlock(altarProperties(MapColor.COLOR_PURPLE), RuneAltarBlock.Operation.ENCHANT)
	);

	public static final Block DISENCHANTING_ALTAR = Registry.register(
		BuiltInRegistries.BLOCK,
		id("disenchanting_altar"),
		new RuneAltarBlock(altarProperties(MapColor.COLOR_PURPLE), RuneAltarBlock.Operation.DISENCHANT)
	);

	public static final Block COMBINING_ALTAR = Registry.register(
		BuiltInRegistries.BLOCK,
		id("combining_altar"),
		new RuneAltarBlock(altarProperties(MapColor.COLOR_ORANGE), RuneAltarBlock.Operation.COMBINE)
	);

	public static final Item RUNE_ALTAR_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		id("rune_altar"),
		new BlockItem(RUNE_ALTAR, new Item.Properties())
	);

	public static final Item DISENCHANTING_ALTAR_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		id("disenchanting_altar"),
		new BlockItem(DISENCHANTING_ALTAR, new Item.Properties())
	);

	public static final Item COMBINING_ALTAR_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		id("combining_altar"),
		new BlockItem(COMBINING_ALTAR, new Item.Properties())
	);

	public static final Item RUNIC_SHARD = Registry.register(
		BuiltInRegistries.ITEM,
		id("runic_shard"),
		new Item(new Item.Properties())
	);

	public static final Item VESTIGE_GUIDE = Registry.register(
		BuiltInRegistries.ITEM,
		id("vestige_guide"),
		new VestigeGuideItem(new Item.Properties().stacksTo(1))
	);

	public static final Item RUNIC_COMPASS = Registry.register(
		BuiltInRegistries.ITEM,
		id("runic_compass"),
		new RunicCompassItem(new Item.Properties().stacksTo(1))
	);

	public static final CreativeModeTab RUNE_VESTIGES_TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		id("rune_vestiges"),
		CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
			.title(Component.translatable("itemGroup.runevestiges.rune_vestiges"))
			.icon(() -> new ItemStack(RUNE_ALTAR_ITEM))
			.displayItems((parameters, output) -> {
				output.accept(RUNE_ALTAR_ITEM);
				output.accept(DISENCHANTING_ALTAR_ITEM);
				output.accept(COMBINING_ALTAR_ITEM);
				output.accept(RUNIC_SHARD);
				output.accept(RUNIC_COMPASS);
				output.accept(ModGuide.createGuideBook());
				addAllEnchantedBooks(output, parameters);
			})
			.build()
	);

	private ModContent() {
	}

	public static void register() {
		ModMenus.register();
		ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
			boolean includeActiveBooks = includesActiveEnchantmentBooks(group);
			entries.getDisplayStacks().removeIf(stack -> shouldHideCreativeBook(stack, includeActiveBooks));
			entries.getSearchTabStacks().removeIf(stack -> shouldHideCreativeBook(stack, includeActiveBooks));
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(entries -> {
				entries.accept(RUNE_ALTAR_ITEM);
				entries.accept(DISENCHANTING_ALTAR_ITEM);
				entries.accept(COMBINING_ALTAR_ITEM);
				entries.accept(RUNIC_COMPASS);
			});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
			.register(entries -> {
				entries.accept(RUNIC_SHARD);
				entries.accept(RUNIC_COMPASS);
				entries.accept(ModGuide.createGuideBook());
			});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register(entries -> {
				entries.accept(RUNE_ALTAR_ITEM);
				entries.accept(DISENCHANTING_ALTAR_ITEM);
				entries.accept(COMBINING_ALTAR_ITEM);
				entries.accept(RUNIC_COMPASS);
			});
	}

	public static boolean includesActiveEnchantmentBooks(CreativeModeTab tab) {
		return tab == RUNE_VESTIGES_TAB
			|| tab == BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES)
			|| tab == BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SEARCH);
	}

	public static void addActiveBooksToCreativeTab(
			CreativeModeTab tab,
			CreativeModeTab.ItemDisplayParameters parameters
	) {
			if (tab != BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
				return;
			}

			var displayItems = tab.getDisplayItems();
			var searchItems = tab.getSearchTabDisplayItems();
			for (String enchantmentId : ENCHANTMENTS) {
				for (ItemStack book : createEnchantedBooks(parameters, enchantmentId)) {
					displayItems.add(book);
					searchItems.add(book.copy());
				}
			}
	}

	public static void addGuideBookToCreativeTab(CreativeModeTab tab) {
		CreativeModeTab ingredientsTab =
			BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.INGREDIENTS);
		CreativeModeTab searchTab =
			BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.SEARCH);
		if (tab != RUNE_VESTIGES_TAB && tab != ingredientsTab && tab != searchTab) {
			return;
		}

		var displayItems = tab.getDisplayItems();
		if (displayItems.stream().noneMatch(stack -> stack.is(VESTIGE_GUIDE))) {
			displayItems.add(ModGuide.createGuideBook());
		}

		var searchItems = tab.getSearchTabDisplayItems();
		if (searchItems.stream().noneMatch(stack -> stack.is(VESTIGE_GUIDE))) {
			searchItems.add(ModGuide.createGuideBook());
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(RuneVestiges.MOD_ID, path);
	}

	public static boolean shouldHideCreativeBook(ItemStack stack, boolean runicTab) {
		if (!stack.is(Items.ENCHANTED_BOOK)) {
			return false;
		}

		return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(holder -> {
			var key = holder.unwrapKey();
			if (key.isEmpty() || !key.orElseThrow().location().getNamespace().equals(RuneVestiges.MOD_ID)) {
				return false;
			}
			return !runicTab || !ACTIVE_ENCHANTMENTS.contains(key.orElseThrow().location().getPath());
		});
	}

	static ItemStack createEnchantedBook(Holder<Enchantment> enchantment, int level) {
		ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
		var enchantmentKey = enchantment.unwrapKey()
			.orElseThrow(() -> new IllegalStateException("A runic book requires a registered enchantment."))
			.location();
		if (enchantmentKey.getNamespace().equals(RuneVestiges.MOD_ID)) {
			book.set(
				DataComponents.CUSTOM_NAME,
				Component.translatable(
					"item.runevestiges.creative_enchanted_book",
					Component.translatable("enchantment.runevestiges." + enchantmentKey.getPath()),
					Component.translatable("enchantment.level." + level)
				)
			);
			book.set(
				DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(runicBookModelData(enchantmentKey.getPath(), level))
			);
		} else {
			book.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
		}
		book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return book;
	}

	public static Optional<Component> runicBookName(ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_BOOK) || stack.has(DataComponents.CUSTOM_NAME)) {
			return Optional.empty();
		}

		var enchantments = runicBookEnchantments(stack);
		if (enchantments.size() != 1) {
			return Optional.empty();
		}

		var key = enchantments.keySet().iterator().next().unwrapKey();
		if (key.isEmpty() || !key.orElseThrow().location().getNamespace().equals(RuneVestiges.MOD_ID)) {
			return Optional.empty();
		}

		String enchantmentId = key.orElseThrow().location().getPath();
		if (!ACTIVE_ENCHANTMENTS.contains(enchantmentId)) {
			return Optional.empty();
		}

		return Optional.of(Component.translatable(
			"item.runevestiges.loot_enchanted_book",
			Component.translatable("enchantment.runevestiges." + enchantmentId)
		));
	}

	public static boolean hasActiveRunicEnchantment(ItemStack stack) {
		if (!stack.is(Items.ENCHANTED_BOOK)) {
			return false;
		}

		return runicBookEnchantments(stack).keySet().stream().anyMatch(holder -> {
			var key = holder.unwrapKey();
			return key.isPresent()
				&& key.orElseThrow().location().getNamespace().equals(RuneVestiges.MOD_ID)
				&& ACTIVE_ENCHANTMENTS.contains(key.orElseThrow().location().getPath());
		});
	}

	public static float runicBookModelPredicateValue(ItemStack stack) {
		if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
			return 0.0F;
		}

		var enchantments = runicBookEnchantments(stack);
		if (enchantments.size() != 1) {
			return hasActiveRunicEnchantment(stack) ? 1.0F : 0.0F;
		}

		var entry = enchantments.entrySet().iterator().next();
		var key = entry.getKey().unwrapKey();
		if (key.isEmpty() || !key.orElseThrow().location().getNamespace().equals(RuneVestiges.MOD_ID)) {
			return 0.0F;
		}

		String enchantmentId = key.orElseThrow().location().getPath();
		int modelPredicateValue = 10;
		for (String candidate : ENCHANTMENTS) {
			int maxLevel = maxEnchantmentLevel(candidate);
			if (candidate.equals(enchantmentId)) {
				int level = entry.getValue();
				if (level < 1 || level > maxLevel) {
					return 1.0F;
				}
				return modelPredicateValue + level - 1;
			}
			modelPredicateValue += maxLevel;
		}

		return hasActiveRunicEnchantment(stack) ? 1.0F : 0.0F;
	}

	private static ItemEnchantments runicBookEnchantments(ItemStack stack) {
		return stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	private static int runicBookModelData(String enchantmentId, int level) {
		int modelData = 10;
		for (String candidate : ENCHANTMENTS) {
			int maxLevel = maxEnchantmentLevel(candidate);
			if (candidate.equals(enchantmentId)) {
				return level >= 1 && level <= maxLevel ? modelData + level - 1 : 1;
			}
			modelData += maxLevel;
		}
		return 1;
	}

	static CustomModelData runicBookCustomModelData(String enchantmentId) {
		return new CustomModelData(runicBookModelData(enchantmentId, 1));
	}

	static String[] enchantmentIds() {
		return ENCHANTMENTS.clone();
	}

	public static int maxEnchantmentLevel(String enchantmentId) {
		return switch (enchantmentId) {
			case "melting", "nightwatcher", "sticky_pants", "lift_off", "lumberlord",
				"obsidian_armor", "rune_of_salvation", "rejuvenation" -> 1;
			case "endurance" -> 5;
			default -> 3;
		};
	}

	private static BlockBehaviour.Properties altarProperties(MapColor color) {
		return BlockBehaviour.Properties.of()
			.mapColor(color)
			.strength(4.0F, 6.0F)
			.noOcclusion()
			.sound(SoundType.DEEPSLATE_TILES);
	}

	private static void addEnchantedBooks(
		CreativeModeTab.Output output,
		CreativeModeTab.ItemDisplayParameters parameters,
		String enchantmentId
	) {
		for (ItemStack book : createEnchantedBooks(parameters, enchantmentId)) {
			output.accept(book);
		}
	}

	private static List<ItemStack> createEnchantedBooks(
		CreativeModeTab.ItemDisplayParameters parameters,
		String enchantmentId
	) {
		ResourceKey<Enchantment> key = ResourceKey.create(
			Registries.ENCHANTMENT,
			id(enchantmentId)
		);
		Holder<Enchantment> enchantment = parameters.holders()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(key);
		int maxLevel = enchantment.value().getMaxLevel();
		List<ItemStack> books = new ArrayList<>(maxLevel);

		for (int level = 1; level <= maxLevel; level++) {
			ItemStack book = createEnchantedBook(enchantment, level);
			books.add(book);
		}
		return books;
	}

	private static void addAllEnchantedBooks(
		CreativeModeTab.Output output,
		CreativeModeTab.ItemDisplayParameters parameters
	) {
		for (String enchantmentId : ENCHANTMENTS) {
			addEnchantedBooks(output, parameters, enchantmentId);
		}
	}
}
