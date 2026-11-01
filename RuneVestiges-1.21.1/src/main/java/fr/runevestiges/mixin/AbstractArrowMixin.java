package fr.runevestiges.mixin;

import fr.runevestiges.content.ModEnchantmentEffects;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void runevestiges$applyArrowEnchantmentEffects(EntityHitResult hit, CallbackInfo callback) {
		ModEnchantmentEffects.onArrowHit((AbstractArrow) (Object) this, hit.getEntity());
	}

	@Inject(method = "onHitBlock", at = @At("HEAD"))
	private void runevestiges$applyHookshot(BlockHitResult hit, CallbackInfo callback) {
		ModEnchantmentEffects.onProjectileHitBlock((AbstractArrow) (Object) this, hit.getBlockPos());
	}
}
