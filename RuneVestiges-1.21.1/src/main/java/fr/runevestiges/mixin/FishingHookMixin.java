package fr.runevestiges.mixin;

import fr.runevestiges.content.ModEnchantmentEffects;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
abstract class FishingHookMixin {
	@Inject(method = "retrieve", at = @At("HEAD"))
	private void runevestiges$applyHookingDamage(ItemStack rod, CallbackInfoReturnable<Integer> callback) {
		FishingHook hook = (FishingHook) (Object) this;
		if (hook.getPlayerOwner() != null) {
			ModEnchantmentEffects.onFishingHookRetrieve(hook.getPlayerOwner(), hook.getHookedIn());
		}
	}
}
