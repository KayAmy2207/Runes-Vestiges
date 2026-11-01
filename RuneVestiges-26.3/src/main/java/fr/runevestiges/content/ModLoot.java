package fr.runevestiges.content;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.UniformGenerator;

public final class ModLoot {
    private ModLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            String namespace = key.identifier().getNamespace();
            String path = key.identifier().getPath();
            if (source.isBuiltin()
                && key.identifier().equals(Identifier.withDefaultNamespace("chests/abandoned_mineshaft"))) {
                table.withPool(LootPool.lootPool()
                    .setRolls(uniformInt(0, 1))
                    .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                        .apply(EnchantWithLevelsFunction.enchantWithLevels(
                            registries.lookupOrThrow(Registries.ENCHANTMENT),
                            uniformInt(10, 24)
                        )))
                );
            }
            if (source.isBuiltin() && namespace.equals("minecraft") && path.startsWith("chests/bastion_")) {
                table.withPool(runicBookPool(
                    registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey("obsidian_armor")),
                    "obsidian_armor",
                    0.2F
                ));
            }
            if (source.isBuiltin()
                && key.identifier().equals(Identifier.withDefaultNamespace("chests/end_city_treasure"))) {
                table.withPool(LootPool.lootPool()
                    .setRolls(Holder.direct(new ConstantValue(1)))
                    .when(LootItemRandomChanceCondition.randomChance(0.35F))
                    .add(LootItem.lootTableItem(ModContent.RUNIC_COMPASS))
                );
                table.withPool(runicBookPool(
                    registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey("rune_of_salvation")),
                    "rune_of_salvation",
                    0.12F
                ));
            }
            if (key.identifier().equals(Identifier.fromNamespaceAndPath("runevestiges", "chests/wizard_tower"))) {
                table.withPool(runicBookPool(
                    registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantmentKey("rejuvenation")),
                    "rejuvenation",
                    0.15F
                ));
            }
        });
    }

    private static Holder<ContextIntProvider> uniformInt(int min, int max) {
        return Holder.direct(new UniformGenerator(
            Holder.direct(new ConstantValue(min)),
            Holder.direct(new ConstantValue(max))
        ));
    }

    private static LootPool.Builder runicBookPool(Holder<Enchantment> enchantment, String enchantmentId, float chance) {
        return LootPool.lootPool()
            .setRolls(Holder.direct(new ConstantValue(1)))
            .when(LootItemRandomChanceCondition.randomChance(chance))
            .add(LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                .apply(new SetEnchantmentsFunction.Builder()
                    .withEnchantment(enchantment, Holder.direct(new ConstantValue(1))))
                .apply(SetComponentsFunction.setComponent(
                    DataComponents.CUSTOM_MODEL_DATA,
                    ModContent.runicBookCustomModelData(enchantmentId)
                )));
    }

    private static ResourceKey<Enchantment> enchantmentKey(String path) {
        return ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath("runevestiges", path)
        );
    }
}
