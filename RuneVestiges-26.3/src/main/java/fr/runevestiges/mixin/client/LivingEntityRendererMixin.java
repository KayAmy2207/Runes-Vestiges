package fr.runevestiges.mixin.client;

import fr.runevestiges.client.RunicCuratorRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntityRenderer.class})
abstract class LivingEntityRendererMixin {
   @Inject(
      method = {"getRenderType"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void runevestiges$hideVanillaCuratorModel(
      LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> callback
   ) {
      if (state instanceof VillagerRenderState villagerState && ((RunicCuratorRenderState)villagerState).runevestiges$isCurator()) {
         callback.setReturnValue(null);
      }
   }
}
