package fr.runevestiges.content;

import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
   public static final BlockEntityType<RuneAltarBlockEntity> RUNE_ALTAR = (BlockEntityType<RuneAltarBlockEntity>)Registry.register(
      BuiltInRegistries.BLOCK_ENTITY_TYPE,
      Identifier.fromNamespaceAndPath("runevestiges", "rune_altar"),
      new BlockEntityType(RuneAltarBlockEntity::new, Set.of(ModContent.RUNE_ALTAR, ModContent.DISENCHANTING_ALTAR, ModContent.COMBINING_ALTAR))
   );

   private ModBlockEntities() {
   }

   public static void register() {
   }
}
