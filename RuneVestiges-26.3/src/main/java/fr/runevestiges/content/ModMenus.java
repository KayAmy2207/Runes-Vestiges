package fr.runevestiges.content;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {
   public static final MenuType<RuneAltarMenu> RUNE_ALTAR = (MenuType<RuneAltarMenu>)Registry.register(
      BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath("runevestiges", "rune_altar"), new MenuType(RuneAltarMenu::new, FeatureFlags.VANILLA_SET)
   );

   private ModMenus() {
   }

   public static void register() {
   }
}
