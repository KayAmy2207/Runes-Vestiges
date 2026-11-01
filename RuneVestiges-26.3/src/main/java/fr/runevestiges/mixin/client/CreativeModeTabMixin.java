package fr.runevestiges.mixin.client;

import fr.runevestiges.content.ModContent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CreativeModeTab.class})
abstract class CreativeModeTabMixin {
   @Inject(
      method = {"buildContents"},
      at = {@At("TAIL")}
   )
   private void runevestiges$hideRetiredEnchantmentBooks(ItemDisplayParameters parameters, CallbackInfo callback) {
      CreativeModeTab tab = (CreativeModeTab)(Object)this;
      ModContent.addActiveBooksToCreativeTab(tab, parameters);
      ModContent.addGuideBookToCreativeTab(tab);
      boolean includeActiveBooks = ModContent.includesActiveEnchantmentBooks(tab);
      tab.getDisplayItems().removeIf(stack -> ModContent.shouldHideCreativeBook(stack, includeActiveBooks));
      tab.getSearchTabDisplayItems().removeIf(stack -> ModContent.shouldHideCreativeBook(stack, includeActiveBooks));
   }
}
