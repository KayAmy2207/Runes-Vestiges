package fr.runevestiges.economy;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ShopPricing {
	private static final Map<String, Long> PRICE_OVERRIDES = Map.ofEntries(
		Map.entry("minecraft:coal", 2L),
		Map.entry("minecraft:coal_ore", 2L),
		Map.entry("minecraft:deepslate_coal_ore", 2L),
		Map.entry("minecraft:iron_ingot", 5L),
		Map.entry("minecraft:raw_iron", 3L),
		Map.entry("minecraft:iron_block", 45L),
		Map.entry("minecraft:iron_ore", 5L),
		Map.entry("minecraft:deepslate_iron_ore", 5L),
		Map.entry("minecraft:copper_ingot", 2L),
		Map.entry("minecraft:raw_copper", 1L),
		Map.entry("minecraft:copper_ore", 2L),
		Map.entry("minecraft:deepslate_copper_ore", 2L),
		Map.entry("minecraft:gold_ingot", 8L),
		Map.entry("minecraft:raw_gold", 5L),
		Map.entry("minecraft:gold_block", 72L),
		Map.entry("minecraft:gold_ore", 8L),
		Map.entry("minecraft:deepslate_gold_ore", 8L),
		Map.entry("minecraft:nether_gold_ore", 8L),
		Map.entry("minecraft:lapis_lazuli", 2L),
		Map.entry("minecraft:lapis_ore", 2L),
		Map.entry("minecraft:deepslate_lapis_ore", 2L),
		Map.entry("minecraft:redstone", 1L),
		Map.entry("minecraft:redstone_ore", 1L),
		Map.entry("minecraft:deepslate_redstone_ore", 1L),
		Map.entry("minecraft:quartz", 3L),
		Map.entry("minecraft:nether_quartz_ore", 3L),
		Map.entry("minecraft:diamond", 20L),
		Map.entry("minecraft:diamond_block", 180L),
		Map.entry("minecraft:diamond_ore", 20L),
		Map.entry("minecraft:deepslate_diamond_ore", 20L),
		Map.entry("minecraft:emerald", 16L),
		Map.entry("minecraft:emerald_block", 144L),
		Map.entry("minecraft:emerald_ore", 16L),
		Map.entry("minecraft:deepslate_emerald_ore", 16L),
		Map.entry("minecraft:ancient_debris", 30L),
		Map.entry("minecraft:netherite_scrap", 35L),
		Map.entry("minecraft:netherite_ingot", 140L),
		Map.entry("minecraft:netherite_block", 1_260L),
		Map.entry("minecraft:amethyst_shard", 5L),
		Map.entry("minecraft:prismarine_shard", 4L),
		Map.entry("minecraft:prismarine_crystals", 8L),
		Map.entry("minecraft:echo_shard", 25L),
		Map.entry("minecraft:heart_of_the_sea", 50L),
		Map.entry("minecraft:shulker_shell", 20L),
		Map.entry("minecraft:elytra", 75L),
		Map.entry("minecraft:totem_of_undying", 50L),
		Map.entry("minecraft:nether_star", 150L),
		Map.entry("minecraft:dragon_egg", 500L)
	);

	private ShopPricing() {
	}

	public static long unitPrice(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0L;
		}

		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		Long override = PRICE_OVERRIDES.get(itemId);
		if (override != null) {
			return override;
		}

		return rarityPrice(stack.getRarity());
	}

	public static long rarityPrice(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 1L;
			case UNCOMMON -> 4L;
			case RARE -> 12L;
			case EPIC -> 30L;
		};
	}

	public static long totalPrice(ItemStack stack) {
		long unitPrice = unitPrice(stack);
		if (unitPrice == 0L) {
			return 0L;
		}
		if (Long.MAX_VALUE / unitPrice < stack.getCount()) {
			return Long.MAX_VALUE;
		}
		return unitPrice * stack.getCount();
	}

}
