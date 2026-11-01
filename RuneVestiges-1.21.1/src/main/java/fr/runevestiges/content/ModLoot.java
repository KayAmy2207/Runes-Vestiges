package fr.runevestiges.content;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public final class ModLoot {
	private ModLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			String namespace = key.location().getNamespace();
			String path = key.location().getPath();
			if (source.isBuiltin()
				&& key.location().equals(ResourceLocation.withDefaultNamespace("chests/abandoned_mineshaft"))) {
				table.withPool(LootPool.lootPool()
					.setRolls(UniformGenerator.between(0.0F, 1.0F))
					.add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
						.apply(EnchantWithLevelsFunction.enchantWithLevels(registries, UniformGenerator.between(10.0F, 24.0F))))
				);
			}
			if (source.isBuiltin() && namespace.equals("minecraft") && path.startsWith("chests/bastion_")) {
				table.withPool(runicBookPool(
					registries.lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(enchantmentKey("obsidian_armor")),
					0.2F
				));
			}
			if (source.isBuiltin() && key.location().equals(ResourceLocation.withDefaultNamespace("chests/end_city_treasure"))) {
				table.withPool(LootPool.lootPool()
					.setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
					.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(0.35F))
					.add(LootItem.lootTableItem(ModContent.RUNIC_COMPASS))
				);
				table.withPool(runicBookPool(
					registries.lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(enchantmentKey("rune_of_salvation")),
					0.12F
				));
			}
			if (key.location().equals(ResourceLocation.fromNamespaceAndPath("runevestiges", "chests/wizard_tower"))) {
				table.withPool(runicBookPool(
					registries.lookupOrThrow(Registries.ENCHANTMENT)
						.getOrThrow(enchantmentKey("rejuvenation")),
					0.15F
				));
			}
		});
	}

	private static LootPool.Builder runicBookPool(
		Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment,
		float chance
	) {
		return LootPool.lootPool()
			.setRolls(ConstantValue.exactly(1.0F))
			.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(chance))
			.add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
				.apply(new SetEnchantmentsFunction.Builder()
					.withEnchantment(enchantment, ConstantValue.exactly(1.0F))));
	}

	private static ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantmentKey(String path) {
		return ResourceKey.create(
			Registries.ENCHANTMENT,
			ResourceLocation.fromNamespaceAndPath("runevestiges", path)
		);
	}
}
