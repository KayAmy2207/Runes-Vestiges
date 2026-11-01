package fr.runevestiges.mixin;

import fr.runevestiges.content.ModContent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ItemStack.class})
abstract class RunicBookNameMixin {
   @Inject(
      method = {"getHoverName"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void runevestiges$nameRunicBook(CallbackInfoReturnable<Component> callback) {
      ModContent.runicBookName((ItemStack)(Object)this).ifPresent(callback::setReturnValue);
   }
}
