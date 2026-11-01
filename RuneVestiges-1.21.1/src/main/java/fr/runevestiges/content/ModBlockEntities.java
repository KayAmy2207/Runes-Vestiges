package fr.runevestiges.content;

import fr.runevestiges.RuneVestiges;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<RuneAltarBlockEntity> RUNE_ALTAR = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		ResourceLocation.fromNamespaceAndPath(RuneVestiges.MOD_ID, "rune_altar"),
		BlockEntityType.Builder.of(
			RuneAltarBlockEntity::new,
			ModContent.RUNE_ALTAR,
			ModContent.DISENCHANTING_ALTAR,
			ModContent.COMBINING_ALTAR
		).build(null)
	);

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
