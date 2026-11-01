package fr.runevestiges.content;

import fr.runevestiges.RuneVestiges;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
	public static final MenuType<RuneAltarMenu> RUNE_ALTAR = Registry.register(
		BuiltInRegistries.MENU,
		ResourceLocation.fromNamespaceAndPath(RuneVestiges.MOD_ID, "rune_altar"),
		new MenuType<>(RuneAltarMenu::new, FeatureFlags.VANILLA_SET)
	);

	private ModMenus() {
	}

	public static void register() {
	}
}
