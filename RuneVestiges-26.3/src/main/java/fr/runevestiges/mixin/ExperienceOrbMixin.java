package fr.runevestiges.mixin;

import fr.runevestiges.content.ModEnchantmentEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ExperienceOrb.class})
abstract class ExperienceOrbMixin {
   @Inject(
      method = {"playerTouch"},
      at = {@At("HEAD")}
   )
   private void runevestiges$applyExperienceEnchantment(Player player, CallbackInfo callback) {
      ModEnchantmentEffects.onExperienceOrbPickup((ExperienceOrb)(Object)this, player);
   }
}
