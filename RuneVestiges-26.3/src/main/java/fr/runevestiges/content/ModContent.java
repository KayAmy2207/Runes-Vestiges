package fr.runevestiges.content;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutput;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.ModifyOutputAll;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import net.minecraft.world.item.CreativeModeTab.Output;
import net.minecraft.world.item.CreativeModeTab.Row;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

public final class ModContent {
   private static final String[] ENCHANTMENTS = new String[]{
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
   public static final Block RUNE_ALTAR = (Block)Registry.register(
      BuiltInRegistries.BLOCK, id("rune_altar"), new RuneAltarBlock(altarProperties(MapColor.COLOR_PURPLE, "rune_altar"), RuneAltarBlock.Operation.ENCHANT)
   );
   public static final Block DISENCHANTING_ALTAR = (Block)Registry.register(
      BuiltInRegistries.BLOCK,
      id("disenchanting_altar"),
      new RuneAltarBlock(altarProperties(MapColor.COLOR_PURPLE, "disenchanting_altar"), RuneAltarBlock.Operation.DISENCHANT)
   );
   public static final Block COMBINING_ALTAR = (Block)Registry.register(
      BuiltInRegistries.BLOCK,
      id("combining_altar"),
      new RuneAltarBlock(altarProperties(MapColor.COLOR_ORANGE, "combining_altar"), RuneAltarBlock.Operation.COMBINE)
   );
   public static final Item RUNE_ALTAR_ITEM = (Item)Registry.register(
      BuiltInRegistries.ITEM, id("rune_altar"), new BlockItem(RUNE_ALTAR, itemProperties("rune_altar"))
   );
   public static final Item DISENCHANTING_ALTAR_ITEM = (Item)Registry.register(
      BuiltInRegistries.ITEM, id("disenchanting_altar"), new BlockItem(DISENCHANTING_ALTAR, itemProperties("disenchanting_altar"))
   );
   public static final Item COMBINING_ALTAR_ITEM = (Item)Registry.register(
      BuiltInRegistries.ITEM, id("combining_altar"), new BlockItem(COMBINING_ALTAR, itemProperties("combining_altar"))
   );
   public static final Item RUNIC_SHARD = (Item)Registry.register(BuiltInRegistries.ITEM, id("runic_shard"), new Item(itemProperties("runic_shard")));
   public static final Item VESTIGE_GUIDE = (Item)Registry.register(
      BuiltInRegistries.ITEM, id("vestige_guide"), new VestigeGuideItem(itemProperties("vestige_guide").stacksTo(1))
   );
   public static final Item RUNIC_COMPASS = (Item)Registry.register(
      BuiltInRegistries.ITEM, id("runic_compass"), new RunicCompassItem(itemProperties("runic_compass").stacksTo(1))
   );
   public static final CreativeModeTab RUNE_VESTIGES_TAB = (CreativeModeTab)Registry.register(
      BuiltInRegistries.CREATIVE_MODE_TAB,
      id("rune_vestiges"),
      CreativeModeTab.builder(Row.TOP, 0)
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
      CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((ModifyOutputAll)(group, entries) -> {
         boolean includeActiveBooks = includesActiveEnchantmentBooks(group);
         entries.getDisplayStacks().removeIf(stack -> shouldHideCreativeBook(stack, includeActiveBooks));
         entries.getSearchTabStacks().removeIf(stack -> shouldHideCreativeBook(stack, includeActiveBooks));
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register((ModifyOutput)entries -> {
         entries.accept(RUNE_ALTAR_ITEM);
         entries.accept(DISENCHANTING_ALTAR_ITEM);
         entries.accept(COMBINING_ALTAR_ITEM);
         entries.accept(RUNIC_COMPASS);
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register((ModifyOutput)entries -> {
         entries.accept(RUNIC_SHARD);
         entries.accept(RUNIC_COMPASS);
         entries.accept(ModGuide.createGuideBook());
      });
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyOutput)entries -> {
         entries.accept(RUNE_ALTAR_ITEM);
         entries.accept(DISENCHANTING_ALTAR_ITEM);
         entries.accept(COMBINING_ALTAR_ITEM);
         entries.accept(RUNIC_COMPASS);
      });
   }

   public static boolean includesActiveEnchantmentBooks(CreativeModeTab tab) {
      return tab == RUNE_VESTIGES_TAB
         || tab == BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.TOOLS_AND_UTILITIES)
         || tab == BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.SEARCH);
   }

   public static void addActiveBooksToCreativeTab(CreativeModeTab tab, ItemDisplayParameters parameters) {
      if (tab == BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
         Collection<ItemStack> displayItems = tab.getDisplayItems();
         Collection<ItemStack> searchItems = tab.getSearchTabDisplayItems();

         for (String enchantmentId : ENCHANTMENTS) {
            for (ItemStack book : createEnchantedBooks(parameters, enchantmentId)) {
               displayItems.add(book);
               searchItems.add(book.copy());
            }
         }
      }
   }

   public static void addGuideBookToCreativeTab(CreativeModeTab tab) {
      CreativeModeTab ingredientsTab = (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.INGREDIENTS);
      CreativeModeTab searchTab = (CreativeModeTab)BuiltInRegistries.CREATIVE_MODE_TAB.getValue(CreativeModeTabs.SEARCH);
      if (tab == RUNE_VESTIGES_TAB || tab == ingredientsTab || tab == searchTab) {
         Collection<ItemStack> displayItems = tab.getDisplayItems();
         if (displayItems.stream().noneMatch(stack -> stack.is(VESTIGE_GUIDE))) {
            displayItems.add(ModGuide.createGuideBook());
         }

         Collection<ItemStack> searchItems = tab.getSearchTabDisplayItems();
         if (searchItems.stream().noneMatch(stack -> stack.is(VESTIGE_GUIDE))) {
            searchItems.add(ModGuide.createGuideBook());
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("runevestiges", path);
   }

   public static boolean shouldHideCreativeBook(ItemStack stack, boolean runicTab) {
      return !stack.is(Items.ENCHANTED_BOOK)
         ? false
         : EnchantmentHelper.getEnchantmentsForCrafting(stack)
            .keySet()
            .stream()
            .anyMatch(
               holder -> {
                  Optional<ResourceKey<Enchantment>> key = holder.unwrapKey();
                  return !key.isEmpty() && key.orElseThrow().identifier().getNamespace().equals("runevestiges")
                     ? !runicTab || !ACTIVE_ENCHANTMENTS.contains(key.orElseThrow().identifier().getPath())
                     : false;
               }
            );
   }

   static ItemStack createEnchantedBook(Holder<Enchantment> enchantment, int level) {
      ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
      EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(enchantment, level));
      Identifier enchantmentKey = ((ResourceKey)enchantment.unwrapKey()
            .orElseThrow(() -> new IllegalStateException("A runic book requires a registered enchantment.")))
         .identifier();
      if (enchantmentKey.getNamespace().equals("runevestiges")) {
         book.set(
            DataComponents.CUSTOM_NAME,
            Component.translatable(
               "item.runevestiges.creative_enchanted_book",
               new Object[]{
                  Component.translatable("enchantment.runevestiges." + enchantmentKey.getPath()), Component.translatable("enchantment.level." + level)
               }
            )
         );
         book.set(DataComponents.CUSTOM_MODEL_DATA, customModelData(runicBookModelData(enchantmentKey.getPath(), level)));
      } else {
         book.set(DataComponents.CUSTOM_MODEL_DATA, customModelData(1));
      }

      book.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      return book;
   }

   public static Optional<Component> runicBookName(ItemStack stack) {
      if (stack.is(Items.ENCHANTED_BOOK) && !stack.has(DataComponents.CUSTOM_NAME)) {
         ItemEnchantments enchantments = runicBookEnchantments(stack);
         if (enchantments.size() != 1) {
            return Optional.empty();
         } else {
            Optional<ResourceKey<Enchantment>> key = ((Holder)enchantments.keySet().iterator().next()).unwrapKey();
            if (!key.isEmpty() && key.orElseThrow().identifier().getNamespace().equals("runevestiges")) {
               String enchantmentId = key.orElseThrow().identifier().getPath();
               return !ACTIVE_ENCHANTMENTS.contains(enchantmentId)
                  ? Optional.empty()
                  : Optional.of(
                     Component.translatable(
                        "item.runevestiges.loot_enchanted_book", new Object[]{Component.translatable("enchantment.runevestiges." + enchantmentId)}
                     )
                  );
            } else {
               return Optional.empty();
            }
         }
      } else {
         return Optional.empty();
      }
   }

   public static boolean hasActiveRunicEnchantment(ItemStack stack) {
      return !stack.is(Items.ENCHANTED_BOOK)
         ? false
         : runicBookEnchantments(stack)
            .keySet()
            .stream()
            .anyMatch(
               holder -> {
                  Optional<ResourceKey<Enchantment>> key = holder.unwrapKey();
                  return key.isPresent()
                     && key.orElseThrow().identifier().getNamespace().equals("runevestiges")
                     && ACTIVE_ENCHANTMENTS.contains(key.orElseThrow().identifier().getPath());
               }
            );
   }

   public static float runicBookModelPredicateValue(ItemStack stack) {
      if (stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
         return 0.0F;
      } else {
         ItemEnchantments enchantments = runicBookEnchantments(stack);
         if (enchantments.size() != 1) {
            return hasActiveRunicEnchantment(stack) ? 1.0F : 0.0F;
         } else {
            Entry<Holder<Enchantment>> entry = (Entry<Holder<Enchantment>>)enchantments.entrySet().iterator().next();
            Optional<ResourceKey<Enchantment>> key = ((Holder)entry.getKey()).unwrapKey();
            if (!key.isEmpty() && key.orElseThrow().identifier().getNamespace().equals("runevestiges")) {
               String enchantmentId = key.orElseThrow().identifier().getPath();
               int modelPredicateValue = 10;

               for (String candidate : ENCHANTMENTS) {
                  int maxLevel = maxEnchantmentLevel(candidate);
                  if (candidate.equals(enchantmentId)) {
                     int level = entry.getValue();
                     if (level >= 1 && level <= maxLevel) {
                        return modelPredicateValue + level - 1;
                     }

                     return 1.0F;
                  }

                  modelPredicateValue += maxLevel;
               }

               return hasActiveRunicEnchantment(stack) ? 1.0F : 0.0F;
            } else {
               return 0.0F;
            }
         }
      }
   }

   private static CustomModelData customModelData(int value) {
      return new CustomModelData(List.of((float)value), List.of(), List.of(), List.of());
   }

   static CustomModelData runicBookCustomModelData(String enchantmentId) {
      return customModelData(runicBookModelData(enchantmentId, 1));
   }

   private static ItemEnchantments runicBookEnchantments(ItemStack stack) {
      return (ItemEnchantments)stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
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

   static String[] enchantmentIds() {
      return (String[])ENCHANTMENTS.clone();
   }

   public static int maxEnchantmentLevel(String enchantmentId) {
      return switch (enchantmentId) {
         case "melting", "nightwatcher", "sticky_pants", "lift_off", "lumberlord", "obsidian_armor", "rune_of_salvation", "rejuvenation" -> 1;
         case "endurance" -> 5;
         default -> 3;
      };
   }

   private static Properties altarProperties(MapColor color, String blockId) {
      return Properties.of()
         .mapColor(color)
         .strength(4.0F, 6.0F)
         .noOcclusion()
         .sound(SoundType.DEEPSLATE_TILES)
         .setId(ResourceKey.create(Registries.BLOCK, id(blockId)));
   }

   private static net.minecraft.world.item.Item.Properties itemProperties(String itemId) {
      return new net.minecraft.world.item.Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(itemId)));
   }

   private static void addEnchantedBooks(Output output, ItemDisplayParameters parameters, String enchantmentId) {
      for (ItemStack book : createEnchantedBooks(parameters, enchantmentId)) {
         output.accept(book);
      }
   }

   private static List<ItemStack> createEnchantedBooks(ItemDisplayParameters parameters, String enchantmentId) {
      ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id(enchantmentId));
      Holder<Enchantment> enchantment = parameters.holders().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
      int maxLevel = ((Enchantment)enchantment.value()).getMaxLevel();
      List<ItemStack> books = new ArrayList<>(maxLevel);

      for (int level = 1; level <= maxLevel; level++) {
         ItemStack book = createEnchantedBook(enchantment, level);
         books.add(book);
      }

      return books;
   }

   private static void addAllEnchantedBooks(Output output, ItemDisplayParameters parameters) {
      for (String enchantmentId : ENCHANTMENTS) {
         addEnchantedBooks(output, parameters, enchantmentId);
      }
   }
}
