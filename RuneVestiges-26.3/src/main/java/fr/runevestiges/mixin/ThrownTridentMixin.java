package fr.runevestiges.mixin;

import fr.runevestiges.content.ModEnchantmentEffects;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ThrownTrident.class})
abstract class ThrownTridentMixin {
   @Inject(
      method = {"onHitEntity"},
      at = {@At("HEAD")}
   )
   private void runevestiges$applyVortex(EntityHitResult hit, CallbackInfo callback) {
      ModEnchantmentEffects.onTridentHit((ThrownTrident)(Object)this, hit.getEntity());
   }
}
